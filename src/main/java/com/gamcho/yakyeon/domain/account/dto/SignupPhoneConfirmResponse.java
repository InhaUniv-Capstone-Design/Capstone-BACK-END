package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 가입용 인증번호 확인 응답.
 * 인증번호가 틀린 건 예외가 아니라 verified=false로 돌려준다 (예외로 롤백되면 시도 횟수가 같이 사라져서
 * 10회 제한이 의미가 없어진다). 성공했을 때만 증표(verification_token)와 유효시간이 들어간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignupPhoneConfirmResponse(
        @JsonProperty("verified") boolean verified,
        @JsonProperty("verification_token") String verificationToken,
        @JsonProperty("token_expires_in") Long tokenExpiresIn
) {
}