package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * 복약자 기본 정보. 휴대폰 번호는 응답에 포함하지 않는다(암호화 저장된 민감정보).
 * my_role은 호출자가 이 복약자에 대해 어떤 자격으로 접근했는지(SELF/GUARDIAN)이고,
 * permission_scope는 보호자일 때만 채워진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatientDetailResponse(
        @JsonProperty("patient_id") Long patientId,
        @JsonProperty("name") String name,
        @JsonProperty("birth_date") LocalDate birthDate,
        @JsonProperty("has_account") boolean hasAccount,
        @JsonProperty("my_role") String myRole,
        @JsonProperty("permission_scope") String permissionScope
) {
}