package com.gamcho.yakyeon.domain.device.repository;

import com.gamcho.yakyeon.domain.device.logic.DoseMatcher;
import com.gamcho.yakyeon.domain.device.logic.SignalPairing;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 약통 신호 처리용 SQL. 같은 칸의 신호가 동시에 들어와도 짝짓기와 개수 차감이 어긋나지 않도록
 * "칸 배정 → 신호 → 예정" 순서로 행을 잠그고 읽는다 (수신 처리와 정리 작업이 같은 순서를 지킨다).
 * 모든 값은 이름 붙은 파라미터로 바인딩한다.
 */
@Repository
public class DeviceSignalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DeviceSignalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 충전이 확인되어 쓰는 중인 칸 배정 */
    public record LiveMapping(long mappingId, long patientId, long prescriptionId, OffsetDateTime validFrom) {
    }

    /** 허용 시간이 지났는데 아직 버튼이 안 온 열림 신호 */
    public record StaleIr(long signalId, long deviceId, int compartmentNo, int windowSec) {
    }

    public record IrRow(long signalId, OffsetDateTime occurredAt) {
    }

    /** 이 칸의 쓰는 중인 배정을 잠그고 읽는다. 같은 칸의 신호 처리를 한 번에 하나씩만 하게 하는 열쇠다 */
    public Optional<LiveMapping> lockActiveMapping(long deviceId, int compartmentNo) {
        return jdbc.query("SELECT mapping_id, patient_id, prescription_id, valid_from FROM compartment_mapping "
                                + "WHERE device_id = :deviceId AND compartment_no = :no AND status = 'ACTIVE' FOR UPDATE",
                        new MapSqlParameterSource().addValue("deviceId", deviceId).addValue("no", compartmentNo),
                        (rs, n) -> new LiveMapping(rs.getLong("mapping_id"), rs.getLong("patient_id"),
                                rs.getLong("prescription_id"), rs.getObject("valid_from", OffsetDateTime.class)))
                .stream().findFirst();
    }

    /** 신호를 저장한다. 같은 기기가 같은 신호 번호를 이미 보냈으면 저장하지 않고 비어 있는 값을 돌려준다(중복) */
    public Optional<Long> insertSignal(long deviceId, String eventId, String signalType, int compartmentNo,
                                       OffsetDateTime occurredAt, String clockSource, Long uptimeMs,
                                       OffsetDateTime receivedAt) {
        return jdbc.query("INSERT INTO device_signal (device_id, device_event_id, signal_type, compartment_no, "
                                + "occurred_at, clock_source, device_uptime_ms, received_at) "
                                + "VALUES (:deviceId, :eventId, :type, :no, :occurredAt, :clockSource, :uptime, :receivedAt) "
                                + "ON CONFLICT (device_id, device_event_id) DO NOTHING RETURNING signal_id",
                        new MapSqlParameterSource().addValue("deviceId", deviceId).addValue("eventId", eventId)
                                .addValue("type", signalType).addValue("no", compartmentNo)
                                .addValue("occurredAt", occurredAt).addValue("clockSource", clockSource)
                                .addValue("uptime", uptimeMs).addValue("receivedAt", receivedAt),
                        (rs, n) -> rs.getLong("signal_id"))
                .stream().findFirst();
    }

    /** 아직 처리되지 않은 열림 신호 중 [from, to] 안에 있는 것을 잠그고 읽는다 */
    public List<SignalPairing.Sig> lockUnprocessedIr(long deviceId, int compartmentNo, OffsetDateTime from, OffsetDateTime to) {
        return jdbc.query("SELECT signal_id, occurred_at FROM device_signal "
                        + "WHERE device_id = :deviceId AND compartment_no = :no AND signal_type = 'IR_TRIGGER' "
                        + "AND processed_at IS NULL AND occurred_at >= :from AND occurred_at <= :to "
                        + "ORDER BY occurred_at, signal_id FOR UPDATE",
                new MapSqlParameterSource().addValue("deviceId", deviceId).addValue("no", compartmentNo)
                        .addValue("from", from).addValue("to", to),
                (rs, n) -> new SignalPairing.Sig(rs.getLong("signal_id"),
                        rs.getObject("occurred_at", OffsetDateTime.class)));
    }

    /** 이 칸에서 가장 최근에 복용이 확인된 시각 (반복 누름과 복용 직후의 열림을 걸러내는 기준) */
    public Optional<OffsetDateTime> lastConfirmedAt(long deviceId, int compartmentNo) {
        return jdbc.query("SELECT max(confirmed_at) AS last_at FROM intake_event "
                                + "WHERE device_id = :deviceId AND compartment_no = :no AND confirmed_at IS NOT NULL",
                        new MapSqlParameterSource().addValue("deviceId", deviceId).addValue("no", compartmentNo),
                        (rs, n) -> Optional.ofNullable(rs.getObject("last_at", OffsetDateTime.class)))
                .stream().findFirst().flatMap(o -> o);
    }

    /**
     * 이 약봉지에서 먹었을 수 있는 예정(판정 전, 마감 전, 너무 멀지 않은 예정)을 잠그고 읽는다.
     * 다른 곳(앱)에서 같은 예정을 기록하는 중이면 끝날 때까지 기다린 뒤 다시 평가하므로, 이미 판정된 예정은 빠진다.
     */
    public List<DoseMatcher.Candidate> lockDoseCandidates(long prescriptionId, long patientId,
                                                          OffsetDateTime takenAt, OffsetDateTime latestScheduled) {
        return jdbc.query("SELECT scheduled_dose_id, timing_slot, scheduled_at, normal_until, close_at FROM scheduled_dose "
                        + "WHERE prescription_id = :prescriptionId AND patient_id = :patientId AND status = 'PENDING' "
                        + "AND close_at > :takenAt AND scheduled_at <= :latest "
                        + "ORDER BY scheduled_at, scheduled_dose_id LIMIT 5 FOR UPDATE",
                new MapSqlParameterSource().addValue("prescriptionId", prescriptionId)
                        .addValue("patientId", patientId).addValue("takenAt", takenAt)
                        .addValue("latest", latestScheduled),
                (rs, n) -> new DoseMatcher.Candidate(rs.getLong("scheduled_dose_id"), rs.getString("timing_slot"),
                        rs.getObject("scheduled_at", OffsetDateTime.class),
                        rs.getObject("normal_until", OffsetDateTime.class),
                        rs.getObject("close_at", OffsetDateTime.class)));
    }

    /** 남은 약봉지를 1개 줄이고(0 아래로는 내려가지 않는다) 줄인 뒤의 개수를 돌려준다 */
    public int decrementRemaining(long mappingId) {
        return jdbc.query("UPDATE compartment_mapping SET remaining_count = GREATEST(coalesce(remaining_count, 0) - 1, 0) "
                                + "WHERE mapping_id = :id RETURNING remaining_count",
                        new MapSqlParameterSource("id", mappingId), (rs, n) -> rs.getInt("remaining_count"))
                .stream().findFirst().orElse(0);
    }

    /**
     * 복용 이벤트를 저장하고 만들어진 이벤트 번호를 돌려준다. 예정(scheduled_dose_id)이 있으면 DB 규칙(트리거)이
     * 그 예정을 판정 결과로 바꾼다 (상태, 판정 시각, 복용 시각, 판정 주체 DEVICE).
     */
    public long insertIntakeEvent(long patientId, long deviceId, long mappingId, int compartmentNo, long prescriptionId,
                                  Long irSignalId, Long buttonSignalId, String detection,
                                  OffsetDateTime occurredAt, OffsetDateTime confirmedAt,
                                  Long scheduledDoseId, String timingSlot, String judgement, Integer remainingAfter) {
        return jdbc.query("INSERT INTO intake_event (patient_id, device_id, mapping_id, compartment_no, prescription_id, "
                        + "ir_signal_id, button_signal_id, detection, occurred_at, confirmed_at, "
                        + "scheduled_dose_id, timing_slot, judgement, remaining_after) "
                        + "VALUES (:patientId, :deviceId, :mappingId, :no, :prescriptionId, :ir, :button, :detection, "
                        + ":occurredAt, :confirmedAt, :doseId, :slot, :judgement, :remainingAfter) RETURNING intake_event_id",
                new MapSqlParameterSource().addValue("patientId", patientId).addValue("deviceId", deviceId)
                        .addValue("mappingId", mappingId).addValue("no", compartmentNo)
                        .addValue("prescriptionId", prescriptionId).addValue("ir", irSignalId)
                        .addValue("button", buttonSignalId).addValue("detection", detection)
                        .addValue("occurredAt", occurredAt).addValue("confirmedAt", confirmedAt)
                        .addValue("doseId", scheduledDoseId).addValue("slot", timingSlot)
                        .addValue("judgement", judgement).addValue("remainingAfter", remainingAfter),
                (rs, n) -> rs.getLong(1)).get(0);
    }

    /**
     * 남은 약봉지가 기준 이하가 됐고 아직 알리지 않았다면 "알림 보냄"으로 표시하고 true를 돌려준다.
     * 한 문장으로 확인과 표시를 함께 해서, 같은 칸의 부족 알림이 두 번 나가지 않는다.
     * 약을 더 채워서 기준을 넘으면 표시가 초기화되어(CompartmentMapping.refill) 다시 알릴 수 있다.
     */
    public boolean markLowStockNotifiedIfNeeded(long mappingId) {
        return !jdbc.query("UPDATE compartment_mapping SET low_stock_notified = true WHERE mapping_id = :id "
                                + "AND NOT low_stock_notified AND remaining_count <= low_stock_threshold RETURNING mapping_id",
                        new MapSqlParameterSource("id", mappingId), (rs, n) -> rs.getLong(1))
                .isEmpty();
    }

    public void markProcessed(Collection<Long> signalIds, OffsetDateTime at) {
        if (signalIds.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE device_signal SET processed_at = :at WHERE signal_id IN (:ids) AND processed_at IS NULL",
                new MapSqlParameterSource().addValue("at", at).addValue("ids", signalIds));
    }

    /** 허용 시간(기기별 pair_window_sec)이 지났는데 버튼이 안 온 가장 오래된 열림 신호 (잠그지 않는다) */
    public Optional<StaleIr> findOldestStaleIr(OffsetDateTime now) {
        return jdbc.query("SELECT s.signal_id, s.device_id, s.compartment_no, d.pair_window_sec "
                                + "FROM device_signal s JOIN device d ON d.device_id = s.device_id "
                                + "WHERE s.signal_type = 'IR_TRIGGER' AND s.processed_at IS NULL "
                                + "AND s.received_at + (d.pair_window_sec * interval '1 second') <= :now "
                                + "ORDER BY s.received_at, s.signal_id LIMIT 1",
                        new MapSqlParameterSource("now", now),
                        (rs, n) -> new StaleIr(rs.getLong("signal_id"), rs.getLong("device_id"),
                                rs.getInt("compartment_no"), rs.getInt("pair_window_sec")))
                .stream().findFirst();
    }

    /** 아직 처리되지 않았으면 이 열림 신호를 잠그고 읽는다 (그 사이 버튼과 짝이 됐다면 비어 있다) */
    public Optional<IrRow> lockIrIfUnprocessed(long signalId) {
        return jdbc.query("SELECT signal_id, occurred_at FROM device_signal "
                                + "WHERE signal_id = :id AND processed_at IS NULL FOR UPDATE",
                        new MapSqlParameterSource("id", signalId),
                        (rs, n) -> new IrRow(rs.getLong("signal_id"), rs.getObject("occurred_at", OffsetDateTime.class)))
                .stream().findFirst();
    }
}