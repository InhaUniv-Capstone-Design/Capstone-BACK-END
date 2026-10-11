package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 동의 이력 1건. 호출자가 복약자면 guardian_login_id가, 보호자면 patient_name(마스킹)이 채워지고
 * 해당 없는 쪽 필드는 응답에서 빠진다(NON_NULL).
 * actor_type은 그 행동을 한 주체(GUARDIAN/PATIENT/SYSTEM)이고, consent_subject(누가 동의했는가)와 다르다.
 * verification_id 같은 내부 값은 노출하지 않는다.
 *
 * occurred_at은 UTC 순간(Instant)이라 "2026-10-04T13:33:51.802213Z" 형태로 나간다 (ApiTime 참고).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsentHistoryResponse(
        @JsonProperty("consent_log_id") Long consentLogId,
        @JsonProperty("guardian_link_id") Long guardianLinkId,
        @JsonProperty("action") String action,
        @JsonProperty("actor_type") String actorType,
        @JsonProperty("consent_subject") String consentSubject,
        @JsonProperty("permission_scope") String permissionScope,
        @JsonProperty("legal_rep_name") String legalRepName,
        @JsonProperty("guardian_login_id") String guardianLoginId,
        @JsonProperty("patient_name") String patientName,
        @JsonProperty("occurred_at") Instant occurredAt
) {
}