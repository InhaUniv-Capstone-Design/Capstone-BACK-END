package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * 처방 한 건과 그 안의 약 목록 (처방 단위 조회).
 * 약이 하나도 없는(모두 삭제됐거나 상태 필터에서 제외된) 처방은 목록에 나오지 않는다.
 * 약을 처방 없이 직접 등록한 경우 prescription_id 등은 null이다.
 */
public record PrescriptionResponse(
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("hospital_name") String hospitalName,
        @JsonProperty("pharmacy_name") String pharmacyName,
        @JsonProperty("prescribed_date") LocalDate prescribedDate,
        @JsonProperty("medications") List<MedicationResponse> medications
) {
}