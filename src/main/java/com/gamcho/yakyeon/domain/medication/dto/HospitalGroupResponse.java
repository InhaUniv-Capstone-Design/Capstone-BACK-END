package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 병원별로 모은 약 목록 (groupByHospital=true).
 * 같은 병원의 여러 처방에 흩어진 약을 한 곳에 모은다. 병원명을 모르는 처방은 hospital_name이 null인 한 묶음으로 모인다.
 */
public record HospitalGroupResponse(
        @JsonProperty("hospital_name") String hospitalName,
        @JsonProperty("medications") List<MedicationResponse> medications
) {
}