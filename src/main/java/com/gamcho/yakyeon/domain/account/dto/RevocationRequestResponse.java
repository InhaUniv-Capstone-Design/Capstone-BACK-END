package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 번호가 복약자 번호인지 아닌지와 관계없이 항상 동일한 응답을 준다
 * (공개 API라서 응답 차이로 회원 여부가 드러나면 안 된다).
 */
public record RevocationRequestResponse(
        @JsonProperty("expires_in") long expiresIn
) {
}