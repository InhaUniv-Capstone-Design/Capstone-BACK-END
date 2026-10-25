package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/** 처방(약봉지) 전체 복용 종료 결과. ended_medication_count는 이번에 종료된 약의 수(이미 끝났던 약은 제외)다. */
public record PrescriptionEndResponse(
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("ended_at") OffsetDateTime endedAt,
        @JsonProperty("ended_medication_count") int endedMedicationCount
) {
}