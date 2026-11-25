package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class VerificationConfirmRequest {

    @JsonProperty("code")
    @NotBlank(message = "인증번호를 입력해주세요.")
    @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리입니다.")
    private String code;
}