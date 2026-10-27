package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/**
 * 약통의 상태 보고. 모든 항목이 선택이고, 보내지 않은 항목은 "바뀌지 않았다"로 본다.
 * 본문 없이 보내도 "살아 있다"는 신호로 처리된다.
 */
@Getter
public class DeviceHeartbeatRequest {

    @JsonProperty("firmware_version")
    @Size(max = 20, message = "펌웨어 버전은 20자 이하여야 합니다.")
    private String firmwareVersion;

    @JsonProperty("ir_sensor_ok")
    private Boolean irSensorOk;

    /** 고장 난 IR 센서가 있는 칸 번호 목록 (정상이면 빈 목록) */
    @JsonProperty("ir_fault_compartments")
    @Size(max = 3, message = "고장 칸은 최대 3개까지 보낼 수 있습니다.")
    private List<@Min(1) @Max(3) Integer> irFaultCompartments;

    @JsonProperty("button_ok")
    private Boolean buttonOk;

    /** Wi-Fi 신호 세기 (dBm, 보통 -90 ~ -30) */
    @JsonProperty("wifi_rssi")
    @Min(value = -127, message = "Wi-Fi 신호 세기가 올바르지 않습니다.")
    @Max(value = 0, message = "Wi-Fi 신호 세기가 올바르지 않습니다.")
    private Integer wifiRssi;

    /** 기기가 마지막으로 받아 간 일정 버전 */
    @JsonProperty("schedule_synced_ver")
    @Min(value = 0, message = "일정 버전이 올바르지 않습니다.")
    private Integer scheduleSyncedVer;
}