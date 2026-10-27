package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 알림 목록 한 페이지. total은 조건에 맞는 전체 건수, unread_count는 읽지 않은 전체 건수다 */
public record NotificationListResponse(
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total") long total,
        @JsonProperty("unread_count") long unreadCount,
        @JsonProperty("items") List<NotificationResponse> items
) {
}