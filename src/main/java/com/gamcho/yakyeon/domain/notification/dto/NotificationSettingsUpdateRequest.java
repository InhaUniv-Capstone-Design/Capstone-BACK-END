package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.notification.logic.NotificationSettingKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/** 알림 설정 변경. 바꾸려는 항목만 보내면 되고, 보내지 않은 항목은 그대로다 */
@Getter
public class NotificationSettingsUpdateRequest {

    @JsonProperty("settings")
    @NotEmpty(message = "변경할 설정을 하나 이상 보내주세요.")
    @Size(max = 8, message = "설정은 최대 8개입니다.")
    @Valid
    private List<Item> settings;

    @Getter
    public static class Item {

        @JsonProperty("setting_key")
        @NotNull(message = "설정 항목을 입력해주세요.")
        private NotificationSettingKey settingKey;

        @JsonProperty("enabled")
        @NotNull(message = "켜기/끄기 값을 입력해주세요.")
        private Boolean enabled;
    }
}