package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;

import java.time.OffsetDateTime;

/** 약통 정보. 비밀값과 그 해시는 어떤 경우에도 응답에 넣지 않는다. 시각은 한국 시간(+09:00)이다. */
public record DeviceResponse(
        @JsonProperty("device_id") Long deviceId,
        @JsonProperty("serial_no") String serialNo,
        @JsonProperty("status") DeviceStatus status,
        @JsonProperty("max_compartments") int maxCompartments,
        @JsonProperty("registered_at") OffsetDateTime registeredAt,
        @JsonProperty("is_online") boolean online,
        @JsonProperty("last_seen_at") OffsetDateTime lastSeenAt,
        @JsonProperty("firmware_version") String firmwareVersion,
        @JsonProperty("ir_sensor_ok") Boolean irSensorOk,
        @JsonProperty("button_ok") Boolean buttonOk
) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(d.getDeviceId(), d.getSerialNo(), d.getStatus(), d.getMaxCompartments(),
                kst(d.getRegisteredAt()), d.isOnline(), kst(d.getLastSeenAt()),
                d.getFirmwareVersion(), d.getIrSensorOk(), d.getButtonOk());
    }

    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime();
    }
}