package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * REST API 명세 §1 POST /auth/login 요청
 * FR-AUTH-005~007
 */
@Getter
public class LoginRequest {

    @JsonProperty("login_id")
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(max = 100, message = "아이디가 너무 깁니다.")
    private String loginId;

    @JsonProperty("password")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(max = 128, message = "비밀번호가 너무 깁니다.")
    private String password;
}