package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 가입용 문자 인증 요청 응답. verification_id와 expires_in은 항상 있다.
 *
 * 나머지는 OCTOMO 방식일 때만 있다(개발용 mock 방식에서는 인증번호가 서버 로그로 나가므로 없다):
 * message_text는 사용자가 휴대폰에서 message_to(OCTOMO 대표번호)로 직접 문자로 보내야 하는 인증번호다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignupPhoneSendResponse(
        @JsonProperty("verification_id") Long verificationId,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("message_text") String messageText,
        @JsonProperty("message_to") String messageTo
) {
}