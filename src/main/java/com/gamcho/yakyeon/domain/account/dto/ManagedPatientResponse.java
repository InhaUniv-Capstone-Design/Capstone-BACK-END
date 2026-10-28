package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 내가 볼 수 있는 복약자 한 명. 본인(SELF)이거나, 내가 연동된 보호자(GUARDIAN)인 복약자다.
 * 이름은 전체로 내려준다 (연동된 보호자는 GET /patients/{id}에서도 전체 이름을 볼 수 있다).
 * link_id, permission_scope, linked_at은 보호자로 연동된 복약자일 때만 값이 있다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManagedPatientResponse(
        @JsonProperty("patient_id") Long patientId,
        @JsonProperty("name") String name,
        @JsonProperty("birth_date") LocalDate birthDate,
        @JsonProperty("has_account") boolean hasAccount,
        @JsonProperty("my_role") String myRole,
        @JsonProperty("link_id") Long linkId,
        @JsonProperty("permission_scope") String permissionScope,
        @JsonProperty("linked_at") OffsetDateTime linkedAt
) {
}