package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 처방 등록 결과. medication_ids는 요청에 담긴 약의 순서와 같다. */
public record PrescriptionCreateResponse(
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("medication_ids") List<Long> medicationIds
) {
}