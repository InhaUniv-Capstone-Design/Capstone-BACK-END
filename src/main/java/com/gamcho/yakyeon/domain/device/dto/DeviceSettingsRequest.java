package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 약통 설정 변경(부저·LED). 보낸 항목만 바뀌고 보내지 않은 항목은 그대로다. 하나는 있어야 한다.
 * 알 수 없는 항목(철자가 틀린 이름 등)은 조용히 무시하지 않고 400으로 알려 준다.
 */
@Getter
public class DeviceSettingsRequest {

    @JsonProperty("buzzer_enabled")
    private Boolean buzzerEnabled;

    @JsonProperty("led_enabled")
    private Boolean ledEnabled;

    @JsonIgnore
    private final List<String> unknownFields = new ArrayList<>();

    @JsonAnySetter
    public void captureUnknown(String key, Object value) {
        unknownFields.add(key);
    }

    @JsonIgnore
    @AssertTrue(message = "변경할 수 없는 항목이 포함되어 있습니다. buzzer_enabled와 led_enabled만 보낼 수 있습니다.")
    public boolean isOnlyAllowedFields() {
        return unknownFields.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "buzzer_enabled와 led_enabled 중 하나 이상을 보내주세요.")
    public boolean isAnySettingPresent() {
        return buzzerEnabled != null || ledEnabled != null;
    }
}