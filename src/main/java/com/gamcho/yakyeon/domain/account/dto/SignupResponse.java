package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST API 명세 §1 POST /auth/signup 응답: user_id
 */
public record SignupResponse(@JsonProperty("user_id") Long userId) {
}