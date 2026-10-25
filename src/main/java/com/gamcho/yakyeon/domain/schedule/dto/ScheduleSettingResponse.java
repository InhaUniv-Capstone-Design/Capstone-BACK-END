package com.gamcho.yakyeon.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;

import java.util.List;

/** 처방의 현재 복용 시각. 아침 → 점심 → 저녁 순서이고, dose_time은 "HH:mm" 문자열이다. */
public record ScheduleSettingResponse(
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("settings") List<Item> settings
) {
    public record Item(
            @JsonProperty("timing_slot") TimingSlot timingSlot,
            @JsonProperty("dose_time") String doseTime,
            @JsonProperty("normal_window_min") int normalWindowMin,
            @JsonProperty("close_after_min") int closeAfterMin
    ) {
    }
}