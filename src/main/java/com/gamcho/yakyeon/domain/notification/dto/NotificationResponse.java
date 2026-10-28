package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 알림 한 건. 값이 없는 항목은 응답에서 빠진다. 시각은 한국 시간(+09:00)이다.
 * 본문은 일반 문구만 담는다 (약 이름·병명 같은 건강정보는 푸시 알림과 목록에 넣지 않고, 자세한 내용은 앱 안에서 본다).
 * scheduled_dose_id, intake_event_id, mapping_id, device_id, check_id는 앱이 알림을 눌렀을 때 이동할 화면을 정하는 데 쓴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        @JsonProperty("notification_id") Long notificationId,
        @JsonProperty("patient_id") Long patientId,
        @JsonProperty("notif_type") String notifType,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("read") boolean read,
        @JsonProperty("read_at") OffsetDateTime readAt,
        @JsonProperty("scheduled_dose_id") Long scheduledDoseId,
        @JsonProperty("intake_event_id") Long intakeEventId,
        @JsonProperty("mapping_id") Long mappingId,
        @JsonProperty("device_id") Long deviceId,
        @JsonProperty("check_id") Long checkId
) {
}