package com.gamcho.yakyeon.domain.dose.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 복약 기록·정정용 SQL. 판정 전 예정의 첫 기록은 직접 UPDATE로, 이미 판정된 예정의 정정은 DB의 정정 함수
 * (correct_dose)로 한다 - 정정 함수는 이력(dose_correction_log)을 남기고 약통 열림 기록과의 연결을 정리해 주기 때문이다.
 * 모든 값은 이름 붙은 파라미터로 바인딩한다.
 */
@Repository
public class DoseRecordRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DoseRecordRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DoseState(long doseId, long patientId, String status, OffsetDateTime scheduledAt,
                            OffsetDateTime normalUntil, OffsetDateTime closeAt, OffsetDateTime takenAt) {
    }

    /** 권한을 확인하려고 복약자 번호만 읽는다 (행을 잠그지 않는다 - 권한 없는 사람이 남의 예정을 잠그지 못하게) */
    public Optional<Long> findPatientId(long doseId) {
        return jdbc.query("SELECT patient_id FROM scheduled_dose WHERE scheduled_dose_id = :id",
                        new MapSqlParameterSource("id", doseId), (rs, n) -> rs.getLong("patient_id"))
                .stream().findFirst();
    }

    /** 예정을 읽으면서 잠근다 (같은 예정을 동시에 기록·정정하는 요청이 서로 덮어쓰지 않게) */
    public Optional<DoseState> lock(long doseId) {
        return jdbc.query("SELECT scheduled_dose_id, patient_id, status, scheduled_at, normal_until, close_at, taken_at "
                                + "FROM scheduled_dose WHERE scheduled_dose_id = :id FOR UPDATE",
                        new MapSqlParameterSource("id", doseId),
                        (rs, n) -> new DoseState(rs.getLong("scheduled_dose_id"), rs.getLong("patient_id"),
                                rs.getString("status"), rs.getObject("scheduled_at", OffsetDateTime.class),
                                rs.getObject("normal_until", OffsetDateTime.class),
                                rs.getObject("close_at", OffsetDateTime.class),
                                rs.getObject("taken_at", OffsetDateTime.class)))
                .stream().findFirst();
    }

    /**
     * 판정 전(PENDING) 예정의 첫 기록 (앱에서 "먹었어요"). 이미 누가 판정했으면 0을 돌려준다.
     * 앱에서 직접 판정한 기록이라 decision_source는 MANUAL이고 판정한 계정이 남는다.
     */
    public int recordFirst(long doseId, String status, OffsetDateTime decidedAt, long userId, OffsetDateTime takenAt) {
        return jdbc.update("UPDATE scheduled_dose SET status = :status, decided_at = :decidedAt, "
                        + "decision_source = 'MANUAL', decided_by_user_id = :userId, taken_at = :takenAt "
                        + "WHERE scheduled_dose_id = :id AND status = 'PENDING'",
                new MapSqlParameterSource()
                        .addValue("status", status).addValue("decidedAt", decidedAt)
                        .addValue("userId", userId).addValue("takenAt", takenAt).addValue("id", doseId));
    }

    /**
     * 이미 판정된 예정의 정정 (DB 함수). result는 NORMAL / DELAYED / NOT_TAKEN.
     * 값이 없는 함수라서 "SELECT count(*) FROM (SELECT 함수())"로 실제로 실행되게 부른다.
     * 모든 값에 CAST를 붙여 NULL(복용 시각 없음)도 형식이 분명하게 전달된다.
     */
    public void correct(long doseId, long userId, String result, OffsetDateTime takenAt, OffsetDateTime now) {
        jdbc.queryForObject("SELECT count(*) FROM (SELECT correct_dose(CAST(:id AS bigint), CAST(:userId AS bigint), "
                        + "CAST(:result AS text), CAST(:takenAt AS timestamptz), CAST(:now AS timestamptz))) x",
                new MapSqlParameterSource()
                        .addValue("id", doseId).addValue("userId", userId).addValue("result", result)
                        .addValue("takenAt", takenAt).addValue("now", now),
                Long.class);
    }
}