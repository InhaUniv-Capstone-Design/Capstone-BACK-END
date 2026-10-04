package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * 복약자 등록 응답. 휴대폰 번호는 응답에 포함하지 않는다
 * (암호화된 값이라 그대로 내려줘도 의미 없고, 굳이 복호화해서 돌려줄 이유도 없음 -
 * 필요하면 별도 "내 정보 조회" API에서 마스킹된 형태로 제공).
 */
public record PatientResponse(
        @JsonProperty("patient_id") Long patientId,
        @JsonProperty("name") String name,
        @JsonProperty("birth_date") LocalDate birthDate,
        @JsonProperty("linked_to_self") boolean linkedToSelf
) {
}