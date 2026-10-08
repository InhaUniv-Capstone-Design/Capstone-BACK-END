package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST API 명세 §1 POST /auth/login, POST /auth/refresh 공통 응답.
 * refreshToken은 이 응답에만 원문으로 담기고, 그 이후로는 절대 평문으로 조회할 수 없다
 * (DB엔 해시만 저장하므로 — 클라이언트가 분실하면 재로그인해야 함).
 */
public record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("account_type") String accountType
) {
}