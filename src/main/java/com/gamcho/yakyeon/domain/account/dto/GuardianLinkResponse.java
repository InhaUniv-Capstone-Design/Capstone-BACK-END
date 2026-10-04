package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuardianLinkResponse(
        @JsonProperty("guardian_link_id") Long guardianLinkId,
        @JsonProperty("status") String status
) {
}