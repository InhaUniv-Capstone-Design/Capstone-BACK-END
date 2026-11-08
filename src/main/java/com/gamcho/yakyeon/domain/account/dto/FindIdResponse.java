package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 아이디 찾기 응답. 같은 번호로 복약자 계정과 보호자 계정을 하나씩 만들 수 있어서 계정이 최대 2개 나온다.
 * account_type은 PATIENT(복약자 본인) 또는 GUARDIAN(보호자)이다.
 */
public record FindIdResponse(@JsonProperty("accounts") List<FoundAccount> accounts) {

    public record FoundAccount(
            @JsonProperty("login_id") String loginId,
            @JsonProperty("account_type") String accountType) {
    }
}