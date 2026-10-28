package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** 내 프로필. 휴대폰 번호는 가려서(010-****-5678) 내려주고 원본은 어디서도 내려주지 않는다. */
public record ProfileResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("login_id") String loginId,
        @JsonProperty("account_type") String accountType,
        @JsonProperty("name") String name,
        @JsonProperty("birth_date") LocalDate birthDate,
        @JsonProperty("phone_masked") String phoneMasked
) {
}