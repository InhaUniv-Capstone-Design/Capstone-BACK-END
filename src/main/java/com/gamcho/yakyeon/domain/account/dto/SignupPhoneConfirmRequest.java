package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * POST /auth/phone-verifications/{id}/confirm 요청.
 *
 * code는 인증번호다. 개발용 방식에서는 사용자가 문자로 받아 입력한 값이고, OCTOMO 방식에서는 앱이 받아 둔 message_text를
 * 그대로 다시 보내는 값이다. phone은 OCTOMO 방식에서 필수다(서버에는 번호 원문이 없고 해시만 있어서, 조회할 번호를 다시 받아
 * 인증을 요청한 번호와 같은지 해시로 확인한다). 개발용 방식에서는 보내도 무시한다.
 */
@Getter
public class SignupPhoneConfirmRequest {

    @JsonProperty("code")
    @NotBlank(message = "인증번호를 입력해주세요.")
    @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리입니다.")
    private String code;

    @JsonProperty("phone")
    @Pattern(regexp = InputRules.PHONE_REGEX, message = InputRules.PHONE_MESSAGE)
    private String phone;
}