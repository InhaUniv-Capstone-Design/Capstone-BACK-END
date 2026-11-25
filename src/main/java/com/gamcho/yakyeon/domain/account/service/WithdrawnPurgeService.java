package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.domain.account.logic.PurgePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 탈퇴한 사람의 데이터를 실제로 삭제한다. 탈퇴 API(withdraw_user)는 이름·생년월일·번호를 지우고(익명화) 건강기록은 남겨 두므로,
 * 이 작업이 보관 기간이 지난 뒤 행 자체를 지워서 처방·복용 기록 같은 건강기록까지 없앤다(DB의 연쇄 삭제).
 *
 * 순서(한 번의 실행, 하나의 트랜잭션):
 *  1) 관리자를 잃은 복약자 정리: 앱 계정이 없고, 연동 중인 대리인이 없고, 마지막 연동을 해제한 대리인이 탈퇴한 계정이며,
 *     그 해제가 abandonedPatientDays보다 오래된 복약자를 탈퇴 처리한다(withdraw_patient: 이름 파기, 약통 해제 등).
 *     대리인 계정 행이 아직 있어야 이 판단을 할 수 있어서 계정 삭제보다 먼저 한다.
 *  2) 탈퇴한 복약자 프로필 삭제 (patient 삭제 → 처방, 약, 복용 일정·기록, 약통, 알림, 병용금기 검사가 연쇄 삭제)
 *  3) 탈퇴한 계정 삭제: 복약자 계정은 복약자 보관 기간, 대리인 계정은 대리인 보관 기간 기준
 *     (계정 삭제 → 로그인 토큰, 푸시 토큰, 약관 동의 상태, 알림 설정, 연동이 연쇄 삭제)
 *
 * 남기는 것: 동의·철회 이력(consent_log, agreement_log)은 증빙이라 계정이 없어져도 남는다(번호만 있고 개인 정보는 없다).
 * 접근 기록(access_audit_log)은 보관 기간이 정해지지 않아 이 작업에서 지우지 않는다.
 *
 * 안전장치: 동시에 두 곳에서 돌지 않도록 DB 잠금을 쓰고, 한 번에 처리하는 복약자 수를 제한하고, dryRun이면 지우지 않고 건수만 센다.
 * 로그에는 건수만 남기고 번호·이름은 남기지 않는다.
 */
@Slf4j
@Service
public class WithdrawnPurgeService {

    /** 이 작업끼리만 쓰는 DB 잠금 번호 */
    private static final long LOCK_KEY = 7_270_001L;

    static final String LOCK_SQL = "SELECT pg_try_advisory_xact_lock(:key)";

    /** 관리자를 잃은 복약자: 앱 계정 없음 + 연동 중인 대리인 없음 + 해제된 연동 중 탈퇴한 대리인의 것이 있음 + 마지막 해제가 기준보다 오래됨 */
    static final String ABANDONED_SQL =
            "SELECT p.patient_id FROM patient p "
                    + "WHERE p.status = 'ACTIVE' AND p.user_id IS NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM guardian_link g WHERE g.patient_id = p.patient_id "
                    + "AND g.status IN ('PENDING', 'ACTIVE')) "
                    + "AND EXISTS (SELECT 1 FROM guardian_link g JOIN app_user u ON u.user_id = g.guardian_user_id "
                    + "WHERE g.patient_id = p.patient_id AND g.status = 'REVOKED' AND u.status = 'DELETED') "
                    + "AND (SELECT max(g.revoked_at) FROM guardian_link g WHERE g.patient_id = p.patient_id) < :cutoff "
                    + "ORDER BY p.patient_id LIMIT :limit";

    static final String WITHDRAW_PATIENT_SQL = "SELECT withdraw_patient(?)";

    static final String DELETE_PATIENTS_SQL =
            "DELETE FROM patient WHERE status = 'DELETED' AND deleted_at < :cutoff";
    static final String DELETE_PATIENT_USERS_SQL =
            "DELETE FROM app_user WHERE status = 'DELETED' AND account_type = 'PATIENT' AND deleted_at < :cutoff";
    static final String DELETE_GUARDIAN_USERS_SQL =
            "DELETE FROM app_user WHERE status = 'DELETED' AND account_type = 'GUARDIAN' AND deleted_at < :cutoff";

    static final String COUNT_PATIENTS_SQL =
            "SELECT count(*) FROM patient WHERE status = 'DELETED' AND deleted_at < :cutoff";
    static final String COUNT_PATIENT_USERS_SQL =
            "SELECT count(*) FROM app_user WHERE status = 'DELETED' AND account_type = 'PATIENT' AND deleted_at < :cutoff";
    static final String COUNT_GUARDIAN_USERS_SQL =
            "SELECT count(*) FROM app_user WHERE status = 'DELETED' AND account_type = 'GUARDIAN' AND deleted_at < :cutoff";

    /** 한 번의 실행 결과 (dryRun이면 지우지 않고 "지울 건수"를 담는다) */
    public record Result(boolean skipped, boolean dryRun, int abandonedPatients, long patients, long patientUsers,
                         long guardianUsers) {

        public boolean nothing() {
            return abandonedPatients == 0 && patients == 0 && patientUsers == 0 && guardianUsers == 0;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final PurgePolicy policy;

    public WithdrawnPurgeService(NamedParameterJdbcTemplate jdbc,
                                 @Value("${privacy.purge.patient-retention-days:0}") int patientRetentionDays,
                                 @Value("${privacy.purge.guardian-retention-days:30}") int guardianRetentionDays,
                                 @Value("${privacy.purge.abandoned-patient-days:30}") int abandonedPatientDays,
                                 @Value("${privacy.purge.batch-limit:200}") int batchLimit) {
        this.jdbc = jdbc;
        this.policy = PurgePolicy.of(patientRetentionDays, guardianRetentionDays, abandonedPatientDays, batchLimit);
        if (this.policy.abandonedPatientDays() != abandonedPatientDays) {
            log.warn("privacy.purge.abandoned-patient-days({}일)를 대리인 계정 보관 기간 안으로 조정했습니다: {}일",
                    abandonedPatientDays, this.policy.abandonedPatientDays());
        }
    }

    public PurgePolicy policy() {
        return policy;
    }

    @Transactional
    public Result run(OffsetDateTime now, boolean dryRun) {
        Boolean locked = jdbc.queryForObject(LOCK_SQL, new MapSqlParameterSource("key", LOCK_KEY), Boolean.class);
        if (!Boolean.TRUE.equals(locked)) {
            return new Result(true, dryRun, 0, 0, 0, 0);   // 다른 곳에서 이미 돌고 있다
        }

        // 1) 관리자를 잃은 복약자 정리 (대리인 계정 행이 남아 있을 때 해야 한다)
        List<Long> abandoned = jdbc.query(ABANDONED_SQL,
                new MapSqlParameterSource().addValue("cutoff", policy.abandonedCutoff(now)).addValue("limit", policy.batchLimit()),
                (rs, n) -> rs.getLong(1));
        if (!dryRun) {
            for (Long patientId : abandoned) {
                // 반환값이 없는 DB 함수라 execute로 부른다 (AuthService의 withdraw_user 호출과 같은 방식)
                jdbc.getJdbcOperations().execute(WITHDRAW_PATIENT_SQL, (PreparedStatementCallback<Void>) ps -> {
                    ps.setLong(1, patientId);
                    ps.execute();
                    return null;
                });
            }
        }

        // 2), 3) 보관 기간이 지난 탈퇴 데이터 삭제 (복약자 프로필 → 계정 순서)
        var patientCutoff = new MapSqlParameterSource("cutoff", policy.patientCutoff(now));
        var guardianCutoff = new MapSqlParameterSource("cutoff", policy.guardianCutoff(now));
        long patients;
        long patientUsers;
        long guardianUsers;
        if (dryRun) {
            patients = count(COUNT_PATIENTS_SQL, patientCutoff);
            patientUsers = count(COUNT_PATIENT_USERS_SQL, patientCutoff);
            guardianUsers = count(COUNT_GUARDIAN_USERS_SQL, guardianCutoff);
        } else {
            patients = jdbc.update(DELETE_PATIENTS_SQL, patientCutoff);
            patientUsers = jdbc.update(DELETE_PATIENT_USERS_SQL, patientCutoff);
            guardianUsers = jdbc.update(DELETE_GUARDIAN_USERS_SQL, guardianCutoff);
        }
        return new Result(false, dryRun, abandoned.size(), patients, patientUsers, guardianUsers);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }
}