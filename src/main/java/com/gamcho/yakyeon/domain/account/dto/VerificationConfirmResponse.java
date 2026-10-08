package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerificationConfirmResponse(
        @JsonProperty("verified") boolean verified
) {
}