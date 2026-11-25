package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 인증 문자 발송 요청. 성인 복약자는 바디 자체가 필요 없고(복약자 본인 번호로 발송),
 * 만 14세 미만 복약자일 때만 법정대리인 휴대폰 번호를 받는다.
 * 법정대리인 이름은 여기서 받지 않고 최종 동의(consent) 요청에서 받는다.
 */
@Getter
public class VerificationSendRequest {

    @JsonProperty("legal_rep_phone")
    private String legalRepPhone;
}