package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/** 문자 인증 철회 2단계 - 번호와 인증번호로 철회 실행. 공개 API. */
@Getter
public class RevocationConfirmRequest {

    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    private String phone;

    @JsonProperty("code")
    @NotBlank(message = "인증번호를 입력해주세요.")
    @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리입니다.")
    private String code;
}