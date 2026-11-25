package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * REST API 명세 §1 POST /auth/refresh, POST /auth/logout 공통 요청
 */
@Getter
public class RefreshRequest {

    @JsonProperty("refresh_token")
    @NotBlank(message = "refresh_token이 필요합니다.")
    private String refreshToken;
}