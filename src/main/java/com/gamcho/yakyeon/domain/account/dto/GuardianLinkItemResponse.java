package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 복약자에게 연동된 보호자 한 명. 보호자의 이름은 마스킹해서(홍*동) 내려주고 아이디는 그대로 준다
 * (동의 이력에서도 복약자에게 보호자 아이디가 보인다). 시각은 한국 시간(+09:00)이다.
 * 동의 전(PENDING)에는 consent_subject와 consented_at이 없을 수 있어 응답에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardianLinkItemResponse(
        @JsonProperty("link_id") Long linkId,
        @JsonProperty("guardian_name") String guardianName,
        @JsonProperty("guardian_login_id") String guardianLoginId,
        @JsonProperty("status") String status,
        @JsonProperty("permission_scope") String permissionScope,
        @JsonProperty("consent_subject") String consentSubject,
        @JsonProperty("consented_at") OffsetDateTime consentedAt
) {
}