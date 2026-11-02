package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.logic.DeviceSyncRules;
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
        @JsonProperty("button_ok") Boolean buttonOk,
        @JsonProperty("buzzer_enabled") boolean buzzerEnabled,
        @JsonProperty("led_enabled") boolean ledEnabled,
        /** 약통이 마지막으로 새 일정을 받았다고 보고한 시각 (한 번도 없으면 응답에서 빠진다) */
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("schedule_synced_at") OffsetDateTime scheduleSyncedAt,
        /** 약통이 서버의 최신 복약 일정을 받았는지. 동기화 버튼은 이 값을 다시 읽어 보여 준다 */
        @JsonProperty("schedule_in_sync") boolean scheduleInSync
) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(d.getDeviceId(), d.getSerialNo(), d.getStatus(), d.getMaxCompartments(),
                kst(d.getRegisteredAt()), d.isOnline(), kst(d.getLastSeenAt()),
                d.getFirmwareVersion(), d.getIrSensorOk(), d.getButtonOk(),
                d.isBuzzerEnabled(), d.isLedEnabled(), kst(d.getScheduleSyncedAt()),
                DeviceSyncRules.isInSync(d.getScheduleVersion(), d.getScheduleSyncedVer()));
    }

    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime();
    }
}