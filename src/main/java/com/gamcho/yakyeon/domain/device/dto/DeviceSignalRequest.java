package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 약통이 보내는 신호 한 건 (칸 열림 또는 복용 확인 버튼).
 * 예: {"device_event_id":"evt-0001","signal_type":"IR_TRIGGER","compartment_no":1,"occurred_at":"2026-10-26T08:05:00+09:00"}
 */
@Getter
public class DeviceSignalRequest {

    /** 기기가 신호마다 붙이는 고유 번호. 같은 번호를 다시 보내면(통신이 끊겨 재전송한 경우) 한 번만 처리한다 */
    @JsonProperty("device_event_id")
    @NotBlank(message = "신호 번호를 입력해주세요.")
    @Size(max = 64, message = "신호 번호는 64자 이하여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "신호 번호는 영문, 숫자, 점, 밑줄, 콜론, 하이픈만 쓸 수 있습니다.")
    private String deviceEventId;

    /** IR_TRIGGER(칸 열림 감지) 또는 BUTTON_PRESS(복용 확인 버튼) */
    @JsonProperty("signal_type")
    @NotBlank(message = "신호 종류를 입력해주세요.")
    @Pattern(regexp = "^(IR_TRIGGER|BUTTON_PRESS)$", message = "신호 종류는 IR_TRIGGER 또는 BUTTON_PRESS여야 합니다.")
    private String signalType;

    @JsonProperty("compartment_no")
    @NotNull(message = "칸 번호를 입력해주세요.")
    @Min(value = 1, message = "칸 번호는 1~3이어야 합니다.")
    @Max(value = 3, message = "칸 번호는 1~3이어야 합니다.")
    private Integer compartmentNo;

    /** 기기가 신호를 감지한 시각 (기기 시계 기준) */
    @JsonProperty("occurred_at")
    @NotNull(message = "신호 발생 시각을 입력해주세요.")
    private OffsetDateTime occurredAt;

    /** 기기 시계의 출처: NTP(인터넷 시각) / RTC(내장 시계) / UPTIME(켜진 뒤 경과 시간). 없으면 NTP */
    @JsonProperty("clock_source")
    @Pattern(regexp = "^(NTP|RTC|UPTIME)$", message = "시계 출처는 NTP, RTC, UPTIME 중 하나여야 합니다.")
    private String clockSource;

    /** 시계 출처가 UPTIME일 때만 보낸다 (기기가 켜진 뒤 경과한 밀리초) */
    @JsonProperty("device_uptime_ms")
    @Min(value = 0, message = "기기 가동 시간이 올바르지 않습니다.")
    private Long deviceUptimeMs;
}