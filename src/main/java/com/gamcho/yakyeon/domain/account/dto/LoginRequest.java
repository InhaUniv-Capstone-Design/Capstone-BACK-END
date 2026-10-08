package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * REST API 명세 §1 POST /auth/login 요청
 * FR-AUTH-005~007
 */
@Getter
public class LoginRequest {

    @JsonProperty("login_id")
    @NotBlank(message = "아이디를 입력해주세요.")
    private String loginId;

    @JsonProperty("password")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}