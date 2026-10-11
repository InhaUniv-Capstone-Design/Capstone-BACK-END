package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * 처방전 OCR 결과. 아무것도 저장되지 않는 확인용 초안이다.
 * 사용자가 확인·수정한 뒤 처방 등록(POST /patients/{patientId}/prescriptions, source=OCR)을 따로 호출한다.
 */
public record OcrRecognizeResponse(
        @JsonProperty("hospital_name") String hospitalName,
        @JsonProperty("pharmacy_name") String pharmacyName,
        @JsonProperty("prescribed_date") LocalDate prescribedDate,
        @JsonProperty("recognized") List<RecognizedMedication> recognized
) {
}