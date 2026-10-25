package com.gamcho.yakyeon.domain.dose.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 복용 일정(예정) 조회용 SQL. 화면 한 장이 예정, 처방(병원 이름), 약 목록, 정정 여부를 한꺼번에 필요로 해서
 * 엔티티를 여러 번 따라가는 대신 SQL로 한 번에 읽는다. 모든 값은 이름 붙은 파라미터로 바인딩한다.
 * 약 목록은 DB 뷰(v_scheduled_dose_item)를 쓴다 - "그 예정 시각에 실제로 포함됐던 약"만 나오는 기준이라,
 * 나중에 약을 종료하거나 삭제해도 지난 예정의 약 목록이 바뀌지 않는다.
 */
@Repository
public class DoseQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DoseQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DoseRow(long doseId, long prescriptionId, String hospitalName, String slot,
                          OffsetDateTime scheduledAt, OffsetDateTime normalUntil, OffsetDateTime closeAt,
                          String status, OffsetDateTime takenAt, String decisionSource, boolean corrected) {
    }

    public record ItemRow(long doseId, long medicationId, String name, BigDecimal dosePerIntake) {
    }

    /**
     * @param from       이 시각 이상
     * @param to         이 시각 미만
     * @param statuses   비어 있으면 모든 상태
     * @param descending 최근 것부터
     */
    public List<DoseRow> findDoses(Long patientId, OffsetDateTime from, OffsetDateTime to,
                                   List<String> statuses, boolean descending) {
        // 정렬 방향은 호출하는 쪽의 boolean으로 고르는 고정 문자열이다 (사용자 입력을 이어 붙이지 않는다)
        String direction = descending ? "DESC" : "ASC";
        StringBuilder sql = new StringBuilder(
                "SELECT d.scheduled_dose_id, d.prescription_id, p.hospital_name, d.timing_slot, "
                        + "       d.scheduled_at, d.normal_until, d.close_at, d.status, d.taken_at, d.decision_source, "
                        + "       EXISTS (SELECT 1 FROM dose_correction_log c WHERE c.scheduled_dose_id = d.scheduled_dose_id) AS corrected "
                        + "  FROM scheduled_dose d JOIN prescription p ON p.prescription_id = d.prescription_id "
                        + " WHERE d.patient_id = :patientId AND d.scheduled_at >= :from AND d.scheduled_at < :to ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("patientId", patientId)
                .addValue("from", from)
                .addValue("to", to);
        if (!statuses.isEmpty()) {
            sql.append(" AND d.status IN (:statuses) ");
            params.addValue("statuses", statuses);
        }
        sql.append(" ORDER BY d.scheduled_at ").append(direction)
                .append(", d.scheduled_dose_id ").append(direction);

        return jdbc.query(sql.toString(), params, (rs, n) -> new DoseRow(
                rs.getLong("scheduled_dose_id"), rs.getLong("prescription_id"), rs.getString("hospital_name"),
                rs.getString("timing_slot"),
                rs.getObject("scheduled_at", OffsetDateTime.class), rs.getObject("normal_until", OffsetDateTime.class),
                rs.getObject("close_at", OffsetDateTime.class), rs.getString("status"),
                rs.getObject("taken_at", OffsetDateTime.class), rs.getString("decision_source"),
                rs.getBoolean("corrected")));
    }

    /** 예정마다 그 시간대에 먹는 약 목록 */
    public Map<Long, List<ItemRow>> findItems(Collection<Long> doseIds) {
        Map<Long, List<ItemRow>> result = new LinkedHashMap<>();
        if (doseIds.isEmpty()) {
            return result;
        }
        String sql = "SELECT scheduled_dose_id, medication_id, input_name, dose_per_intake "
                + "  FROM v_scheduled_dose_item WHERE scheduled_dose_id IN (:ids) "
                + " ORDER BY scheduled_dose_id, medication_id";
        jdbc.query(sql, new MapSqlParameterSource("ids", doseIds), (rs, n) -> {
            ItemRow item = new ItemRow(rs.getLong("scheduled_dose_id"), rs.getLong("medication_id"),
                    rs.getString("input_name"), rs.getBigDecimal("dose_per_intake"));
            result.computeIfAbsent(item.doseId(), k -> new ArrayList<>()).add(item);
            return item;
        });
        return result;
    }
}