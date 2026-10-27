package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.notification.logic.NotificationSettingKey;

import java.util.List;

/** 알림 설정 8개 전체. 처음 바꾸기 전에는 모두 켜짐이다 */
public record NotificationSettingsResponse(
        @JsonProperty("settings") List<Item> settings
) {
    public record Item(
            @JsonProperty("setting_key") NotificationSettingKey settingKey,
            @JsonProperty("enabled") boolean enabled
    ) {
    }
}