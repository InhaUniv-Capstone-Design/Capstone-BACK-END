package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.consent.ConsentRules;

import java.time.OffsetDateTime;

/**
 * 약관 종류별 현재 동의 상태. agreed는 "지금 유효한 동의"이고, 철회했으면 false다.
 * changed_at은 마지막으로 바뀐 시각(동의 시각, 철회했다면 철회 시각)이다.
 */
public record AgreementResponse(
        @JsonProperty("terms_type") String termsType,
        @JsonProperty("terms_version_id") Long termsVersionId,
        @JsonProperty("version") String version,
        @JsonProperty("agreed") boolean agreed,
        @JsonProperty("changed_at") OffsetDateTime changedAt
) {

    public static AgreementResponse from(ConsentRules.State state) {
        return new AgreementResponse(state.termsType(), state.termsVersionId(), state.version(), state.agreed(),
                state.changedAt().atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime());
    }
}