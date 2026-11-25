package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * expires_at(시각) 대신 expires_in(초)을 쓴다 - LocalDateTime 타임존 직렬화 방침이
 * 아직 정해지지 않아서, 시각 필드 노출을 피하려는 것.
 */
public record VerificationSendResponse(
        @JsonProperty("verification_id") Long verificationId,
        @JsonProperty("expires_in") long expiresIn
) {
}