package com.gamcho.yakyeon.domain.device.repository;

import com.gamcho.yakyeon.domain.device.logic.StatusReportPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 기기 상태 보고 이력 (device_status_report). 값이 스무 개 안팎이라 엔티티 없이 SQL로 읽고 쓴다. */
@Repository
public class DeviceStatusReportRepository {

    /**
     * 백엔드가 직접 받은(HTTPS) 보고는 이미 고장을 판정해서 알림까지 보냈으므로 처리된 채로(processed_at) 저장한다.
     * 중계 서버(MQTT)가 저장한 보고는 processed_at이 비어 있고, 정기 작업(DeviceFaultProcessor)이 읽어서 판정한다.
     */
    static final String INSERT_SQL = "INSERT INTO device_status_report "
            + "(device_id, reported_at, ir_sensor_ok, ir_fault_compartments, button_ok, firmware_version, wifi_rssi, processed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /** 고장 판정 전인 보고 번호를 오래된 순으로 읽는다 (잠그지 않는다) */
    static final String FIND_PENDING_SQL =
            "SELECT report_id FROM device_status_report WHERE processed_at IS NULL ORDER BY report_id LIMIT ?";

    /**
     * 판정 전인 보고 한 건을 잠그고 읽는다(그 사이 처리됐다면 비어 있다). 기기가 아직 등록된 상태인지도 함께 읽는다.
     * 잠금은 보고 행에만 건다(FOR UPDATE OF r): 기기 행까지 잠그면 상태 보고·신호 처리가 괜히 기다리게 된다.
     */
    static final String LOCK_PENDING_SQL =
            "SELECT r.report_id, r.device_id, d.patient_id, (d.status = 'REGISTERED') AS registered, "
                    + "r.ir_sensor_ok, r.button_ok, r.ir_fault_compartments "
                    + "FROM device_status_report r JOIN device d ON d.device_id = r.device_id "
                    + "WHERE r.report_id = ? AND r.processed_at IS NULL FOR UPDATE OF r";

    /**
     * 이 보고 직전의 "알려진 마지막 값". 항목마다 그 보고보다 앞선 행 중 값이 NULL이 아닌 가장 최근 값이다
     * (예전 펌웨어가 보내지 않은 항목은 NULL로 저장되는데, NULL은 "직전 값 유지"라는 약속이라서 건너뛰고 거슬러 올라간다).
     * 중계 서버가 device 행을 이미 새 값으로 덮어쓰므로 비교 기준은 device 행이 아니라 이 이력이다.
     */
    static final String PREVIOUS_SQL =
            "SELECT "
                    + "(SELECT ir_sensor_ok FROM device_status_report WHERE device_id = ? AND report_id < ? "
                    + "AND ir_sensor_ok IS NOT NULL ORDER BY report_id DESC LIMIT 1) AS ir_sensor_ok, "
                    + "(SELECT button_ok FROM device_status_report WHERE device_id = ? AND report_id < ? "
                    + "AND button_ok IS NOT NULL ORDER BY report_id DESC LIMIT 1) AS button_ok, "
                    + "(SELECT ir_fault_compartments FROM device_status_report WHERE device_id = ? AND report_id < ? "
                    + "AND ir_fault_compartments IS NOT NULL ORDER BY report_id DESC LIMIT 1) AS ir_fault_compartments";

    static final String MARK_PROCESSED_SQL =
            "UPDATE device_status_report SET processed_at = ? WHERE report_id = ? AND processed_at IS NULL";

    private final JdbcTemplate jdbc;

    /** 판정 전인 보고 한 건. 값이 NULL이면 "이번 보고에 없는 항목"이다 (직전 값이 그대로라는 뜻) */
    public record PendingReport(long reportId, long deviceId, long patientId, boolean registered,
                                Boolean irSensorOk, Boolean buttonOk, List<Integer> irFaultCompartments) {
    }

    /** 이 보고 직전의 알려진 마지막 값. 한 번도 보고하지 않은 항목은 NULL이다 */
    public record PreviousValues(Boolean irSensorOk, Boolean buttonOk, List<Integer> irFaultCompartments) {
    }

    public DeviceStatusReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Last(OffsetDateTime reportedAt, StatusReportPolicy.Values values) {
    }

    /** 가장 최근에 남긴 보고 (기록 여부를 정하는 기준) */
    public Optional<Last> findLast(long deviceId) {
        return jdbc.query(
                        "SELECT reported_at, ir_sensor_ok, button_ok, firmware_version, ir_fault_compartments "
                                + "FROM device_status_report WHERE device_id = ? ORDER BY reported_at DESC LIMIT 1",
                        (rs, n) -> {
                            List<Integer> faults = new ArrayList<>();
                            Array array = rs.getArray("ir_fault_compartments");
                            if (array != null) {
                                for (Object o : (Object[]) array.getArray()) {
                                    faults.add(((Number) o).intValue());
                                }
                            }
                            return new Last(rs.getObject("reported_at", OffsetDateTime.class),
                                    new StatusReportPolicy.Values(rs.getObject("ir_sensor_ok", Boolean.class),
                                            rs.getObject("button_ok", Boolean.class),
                                            rs.getString("firmware_version"), faults));
                        }, deviceId)
                .stream().findFirst();
    }

    public List<Long> findPendingIds(int limit) {
        return jdbc.query(FIND_PENDING_SQL, (rs, n) -> rs.getLong("report_id"), limit);
    }

    public Optional<PendingReport> lockPending(long reportId) {
        return jdbc.query(LOCK_PENDING_SQL, (rs, n) -> new PendingReport(rs.getLong("report_id"), rs.getLong("device_id"),
                        rs.getLong("patient_id"), rs.getBoolean("registered"),
                        rs.getObject("ir_sensor_ok", Boolean.class), rs.getObject("button_ok", Boolean.class),
                        readFaults(rs.getArray("ir_fault_compartments"))), reportId)
                .stream().findFirst();
    }

    public PreviousValues previousValues(long deviceId, long reportId) {
        return jdbc.query(PREVIOUS_SQL, (rs, n) -> new PreviousValues(rs.getObject("ir_sensor_ok", Boolean.class),
                        rs.getObject("button_ok", Boolean.class), readFaults(rs.getArray("ir_fault_compartments"))),
                deviceId, reportId, deviceId, reportId, deviceId, reportId).get(0);
    }

    public void markProcessed(long reportId, OffsetDateTime at) {
        jdbc.update(MARK_PROCESSED_SQL, at, reportId);
    }

    /** SQL의 NULL 배열은 null(보고에 없던 항목), 빈 배열은 빈 목록(고장 난 칸이 없음)으로 구분해서 읽는다 */
    private static List<Integer> readFaults(Array array) throws java.sql.SQLException {
        if (array == null) {
            return null;
        }
        List<Integer> faults = new ArrayList<>();
        for (Object o : (Object[]) array.getArray()) {
            faults.add(((Number) o).intValue());
        }
        return faults;
    }

    public void insert(long deviceId, OffsetDateTime reportedAt, StatusReportPolicy.Values v, Integer wifiRssi) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    INSERT_SQL);
            ps.setLong(1, deviceId);
            ps.setObject(2, reportedAt);
            ps.setObject(3, v.irSensorOk(), Types.BOOLEAN);
            if (v.irFaultCompartments() == null) {
                ps.setNull(4, Types.ARRAY);
            } else {
                ps.setArray(4, con.createArrayOf("int2",
                        v.irFaultCompartments().stream().map(Integer::shortValue).toArray()));
            }
            ps.setObject(5, v.buttonOk(), Types.BOOLEAN);
            ps.setString(6, v.firmwareVersion());
            ps.setObject(7, wifiRssi == null ? null : wifiRssi.shortValue(), Types.SMALLINT);
            ps.setObject(8, reportedAt);   // processed_at: 이 경로는 저장하기 전에 이미 고장을 판정하고 알림까지 보냈다
            return ps;
        });
    }
}