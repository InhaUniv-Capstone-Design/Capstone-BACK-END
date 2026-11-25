package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /patients/search 응답 항목. 이름은 마스킹(홍*동)해서 내려준다 -
 * 보호자가 "내가 찾던 사람이 맞는지" 구분할 최소한의 정보만 노출한다.
 */
public record PatientSearchResponse(
        @JsonProperty("patient_id") Long patientId,
        @JsonProperty("name") String name,
        @JsonProperty("has_account") boolean hasAccount
) {
}