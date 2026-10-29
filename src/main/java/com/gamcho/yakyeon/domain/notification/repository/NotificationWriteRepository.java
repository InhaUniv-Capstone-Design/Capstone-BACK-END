package com.gamcho.yakyeon.domain.notification.repository;

import com.gamcho.yakyeon.domain.notification.logic.NotificationKind;
import com.gamcho.yakyeon.domain.notification.logic.NotificationSettingKey;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 알림 생성 SQL. 받는 사람을 찾는 일과 알림 저장을 SQL 한 문장으로 처리한다.
 *
 * 받는 사람 규칙
 *  - 보호자: 그 복약자와 활성(ACTIVE) 연동이 있는 보호자
 *  - 복약자 본인: 앱 계정이 있는 경우만
 *  - 받는 사람이 해당 알림 설정을 꺼 놨으면(notification_setting.enabled = false) 알림을 만들지 않는다.
 *    설정 행이 없으면 켜짐이다.
 *  - 같은 사건의 알림은 받는 사람당 한 번만 만든다 (dedupe): 예정 번호나 복용 이벤트 번호로 이미 있는지 확인한다.
 *
 * 건강정보를 담지 않는 일반 문구만 저장한다 (NotificationKind).
 */
@Repository
public class NotificationWriteRepository {

    /** 앱이 알림을 눌렀을 때 이동할 화면을 정하는 데 쓰는 관련 번호들 (없으면 null) */
    public record Links(Long scheduledDoseId, Long intakeEventId, Long mappingId, Long deviceId, Long prescriptionId,
                        Long checkId) {

        /** 처방·병용금기 검사와 관계없는 알림(대부분)은 그 번호 없이 만든다 */
        public Links(Long scheduledDoseId, Long intakeEventId, Long mappingId, Long deviceId) {
            this(scheduledDoseId, intakeEventId, mappingId, deviceId, null, null);
        }

        /** 처방에 딸린 알림(복용 종료 예정) */
        public Links(Long scheduledDoseId, Long intakeEventId, Long mappingId, Long deviceId, Long prescriptionId) {
            this(scheduledDoseId, intakeEventId, mappingId, deviceId, prescriptionId, null);
        }
    }

    /** 1시간이 지났는데 아직 복용이 기록되지 않은 예정 */
    public record OverdueDose(long scheduledDoseId, long patientId) {
    }

    /** 곧 복용할 시간이 되는 예정 (복용 시간 알림 대상) */
    public record UpcomingDose(long scheduledDoseId, long patientId) {
    }

    /** 마지막 복용일이 다가오는 처방 (복용 종료 예정 알림 대상) */
    public record EndingPrescription(long prescriptionId, long patientId) {
    }

    private static final String INSERT_COLUMNS = "INSERT INTO notification (patient_id, recipient_user_id, notif_type, "
            + "android_channel, title, body, scheduled_dose_id, intake_event_id, mapping_id, device_id, prescription_id, check_id) ";
    /** 비어 있을 수 있는 번호는 형을 명시해서, 값이 null이어도 DB가 형을 정할 수 있게 한다 */
    private static final String SELECT_VALUES = "SELECT CAST(:patientId AS BIGINT), %s, :type, :channel, :title, :body, "
            + "CAST(:doseId AS BIGINT), CAST(:intakeId AS BIGINT), CAST(:mappingId AS BIGINT), CAST(:deviceId AS BIGINT), "
            + "CAST(:prescriptionId AS BIGINT), CAST(:checkId AS BIGINT) ";

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 연동된 보호자에게 알림을 만든다. 만든 건수를 돌려준다 */
    public int insertForGuardians(long patientId, NotificationKind kind, NotificationSettingKey settingKey,
                                  Links links, boolean dedupe) {
        String recipient = "g.guardian_user_id";
        String sql = INSERT_COLUMNS + String.format(SELECT_VALUES, recipient)
                + "FROM guardian_link g WHERE g.patient_id = :patientId AND g.status = 'ACTIVE' "
                + settingClause(recipient, settingKey) + dedupeClause(recipient, links, dedupe) + conflictClause(links);
        return jdbc.update(sql, params(patientId, kind, settingKey, links));
    }

    /** 복약자 본인에게 알림을 만든다 (앱 계정이 없으면 만들 곳이 없다). 만든 건수를 돌려준다 */
    public int insertForPatient(long patientId, NotificationKind kind, NotificationSettingKey settingKey,
                                Links links, boolean dedupe) {
        String recipient = "p.user_id";
        String sql = INSERT_COLUMNS + String.format(SELECT_VALUES, recipient)
                + "FROM patient p WHERE p.patient_id = :patientId AND p.user_id IS NOT NULL "
                + settingClause(recipient, settingKey) + dedupeClause(recipient, links, dedupe) + conflictClause(links);
        return jdbc.update(sql, params(patientId, kind, settingKey, links));
    }

    /**
     * 알림을 보낼 때가 된 예정(예정 시각에서 기준 시간이 지났고 아직 마감 전이며 판정 전)을 읽는다.
     * 다른 곳(앱, 약통)에서 같은 예정을 기록하는 중이면 건너뛰고(SKIP LOCKED) 다음 주기에 다시 본다.
     * 읽은 예정은 이 트랜잭션이 끝날 때까지 잠겨서, 알림을 만드는 도중 복용이 기록되는 일이 없다.
     */
    public List<OverdueDose> lockOverdueDoses(OffsetDateTime cutoff, OffsetDateTime now, int limit) {
        return jdbc.query("SELECT scheduled_dose_id, patient_id FROM scheduled_dose "
                        + "WHERE status = 'PENDING' AND scheduled_at <= :cutoff AND close_at > :now "
                        + "ORDER BY scheduled_at, scheduled_dose_id LIMIT :limit FOR UPDATE SKIP LOCKED",
                new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("now", now).addValue("limit", limit),
                (rs, n) -> new OverdueDose(rs.getLong("scheduled_dose_id"), rs.getLong("patient_id")));
    }

    /**
     * 곧 복용할 시간이 되는 예정(지금 이후 ~ until 사이에 시작하고 판정 전)을 읽는다. 복용 시간 알림 대상이다.
     * 잠금 방식은 lockOverdueDoses와 같다. close_at 조건은 항상 참이지만 PENDING 전용 인덱스를 쓰게 하려고 넣었다.
     */
    public List<UpcomingDose> lockUpcomingDoses(OffsetDateTime now, OffsetDateTime until, int limit) {
        return jdbc.query("SELECT scheduled_dose_id, patient_id FROM scheduled_dose "
                        + "WHERE status = 'PENDING' AND scheduled_at > :now AND scheduled_at <= :until AND close_at > :now "
                        + "ORDER BY scheduled_at, scheduled_dose_id LIMIT :limit FOR UPDATE SKIP LOCKED",
                new MapSqlParameterSource().addValue("now", now).addValue("until", until).addValue("limit", limit),
                (rs, n) -> new UpcomingDose(rs.getLong("scheduled_dose_id"), rs.getLong("patient_id")));
    }

    /**
     * 마지막 복용일이 [today, until] 사이로 다가온 처방을 읽는다. 복용 종료 예정 알림 대상이다.
     *  - 삭제했거나 복용을 종료한 약, 필요 시 복용 약은 마지막 복용일 계산에서 빼고, 종료된 처방은 대상이 아니다.
     *  - 가장 이른 시작일이 오늘보다 앞인 처방만(오늘 막 시작한 짧은 처방에는 알리지 않는다).
     */
    public List<EndingPrescription> findEndingPrescriptions(LocalDate today, LocalDate until, int limit) {
        return jdbc.query("SELECT pm.prescription_id, pm.patient_id FROM patient_medication pm "
                        + "JOIN prescription p ON p.prescription_id = pm.prescription_id AND p.patient_id = pm.patient_id "
                        + "WHERE pm.deleted_at IS NULL AND pm.ended_at IS NULL AND NOT pm.is_prn AND p.ended_at IS NULL "
                        + "GROUP BY pm.prescription_id, pm.patient_id "
                        + "HAVING MAX(pm.end_date) BETWEEN :today AND :until AND MIN(pm.start_date) < :today "
                        + "ORDER BY MAX(pm.end_date), pm.prescription_id LIMIT :limit",
                new MapSqlParameterSource().addValue("today", today).addValue("until", until).addValue("limit", limit),
                (rs, n) -> new EndingPrescription(rs.getLong("prescription_id"), rs.getLong("patient_id")));
    }

    /** 받는 사람이 이 알림 설정을 꺼 놨으면 제외한다 (설정 행이 없거나 켜짐이면 통과) */
    private static String settingClause(String recipient, NotificationSettingKey key) {
        if (key == null) {
            return "";
        }
        return "AND NOT EXISTS (SELECT 1 FROM notification_setting s WHERE s.user_id = " + recipient
                + " AND s.setting_key = :settingKey AND NOT s.enabled) ";
    }

    /** 같은 사건의 알림이 이미 있는 받는 사람은 제외한다. 사건을 가리키는 번호가 없으면 중복 확인을 하지 않는다 */
    private static String dedupeClause(String recipient, Links links, boolean dedupe) {
        if (!dedupe) {
            return "";
        }
        if (links.scheduledDoseId() != null) {
            return "AND NOT EXISTS (SELECT 1 FROM notification n WHERE n.recipient_user_id = " + recipient
                    + " AND n.notif_type = :type AND n.scheduled_dose_id = :doseId) ";
        }
        if (links.intakeEventId() != null) {
            return "AND NOT EXISTS (SELECT 1 FROM notification n WHERE n.recipient_user_id = " + recipient
                    + " AND n.notif_type = :type AND n.intake_event_id = :intakeId) ";
        }
        return "";
    }

    /**
     * 처방에 딸린 알림(복용 종료 예정)은 DB의 유일 인덱스(uq_notification_end_soon)가 같은 처방·같은 받는 사람의 중복을
     * 막는다. 그 경우 오류 대신 건너뛰도록 한다.
     */
    private static String conflictClause(Links links) {
        return links.prescriptionId() == null ? "" : " ON CONFLICT DO NOTHING";
    }

    private static MapSqlParameterSource params(long patientId, NotificationKind kind, NotificationSettingKey settingKey,
                                                Links links) {
        return new MapSqlParameterSource()
                .addValue("patientId", patientId)
                .addValue("type", kind.type())
                .addValue("channel", kind.channel())
                .addValue("title", kind.title())
                .addValue("body", kind.body())
                .addValue("settingKey", settingKey == null ? null : settingKey.name())
                .addValue("doseId", links.scheduledDoseId())
                .addValue("intakeId", links.intakeEventId())
                .addValue("mappingId", links.mappingId())
                .addValue("deviceId", links.deviceId())
                .addValue("prescriptionId", links.prescriptionId())
                .addValue("checkId", links.checkId());
    }
}