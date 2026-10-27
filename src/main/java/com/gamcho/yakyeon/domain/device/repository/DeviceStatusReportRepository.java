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

    private final JdbcTemplate jdbc;

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

    public void insert(long deviceId, OffsetDateTime reportedAt, StatusReportPolicy.Values v, Integer wifiRssi) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO device_status_report "
                            + "(device_id, reported_at, ir_sensor_ok, ir_fault_compartments, button_ok, firmware_version, wifi_rssi) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)");
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
            return ps;
        });
    }
}