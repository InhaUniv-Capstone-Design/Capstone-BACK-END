package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * REST API 명세 §1 POST /auth/signup 요청
 * FR-AUTH-001~004
 */
@Getter
public class SignupRequest {

    @JsonProperty("login_id")
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 4, max = 30, message = "아이디는 4~30자여야 합니다.")
    private String loginId;

    @JsonProperty("password")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    private String password;

    @JsonProperty("password_confirm")
    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String passwordConfirm;

    @JsonProperty("account_type")
    @NotBlank(message = "계정 유형을 선택해주세요.")
    @Pattern(regexp = "SELF|GUARDIAN", message = "계정 유형은 SELF 또는 GUARDIAN만 가능합니다.")
    private String accountType;
}