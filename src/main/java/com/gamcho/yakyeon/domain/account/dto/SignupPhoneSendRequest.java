package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/** POST /auth/phone-verifications 요청 - 회원가입 화면의 "인증번호 받기" */
@Getter
public class SignupPhoneSendRequest {

    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다. (예: 01012345678)")
    private String phone;
}