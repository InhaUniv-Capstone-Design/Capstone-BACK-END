package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 읽지 않은 알림 수 (앱 종 모양 아이콘의 배지) */
public record UnreadCountResponse(@JsonProperty("unread_count") long unreadCount) {
}