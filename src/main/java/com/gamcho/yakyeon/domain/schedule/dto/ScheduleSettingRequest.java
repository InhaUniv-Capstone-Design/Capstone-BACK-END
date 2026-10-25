package com.gamcho.yakyeon.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/**
 * 처방의 복용 시각 설정. 이 요청이 그 처방의 시각 전체를 정한다 -
 * 요청에 없는 시간대의 기존 시각은 (그 시간대에 먹는 약이 없을 때만) 해제된다.
 */
@Getter
public class ScheduleSettingRequest {

    @JsonProperty("settings")
    @NotNull(message = "복용 시각 목록을 입력해주세요.")
    @Size(max = 3, message = "복용 시각은 아침·점심·저녁 최대 3개까지 설정할 수 있습니다.")
    @Valid
    private List<Item> settings;

    @Getter
    public static class Item {

        @JsonProperty("timing_slot")
        @NotNull(message = "복용 시간대를 입력해주세요. (MORNING, LUNCH, DINNER)")
        private TimingSlot timingSlot;

        /** HH:mm (24시간). 예: 08:00 */
        @JsonProperty("dose_time")
        @NotBlank(message = "복용 시각을 입력해주세요.")
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "복용 시각은 HH:mm 형식이어야 합니다. (예: 08:00)")
        private String doseTime;
    }
}