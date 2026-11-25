package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 틀린 코드·만료·없는 번호 모두 revoked=false, revoked_count=0으로 똑같이 응답한다
 * (실패 사유를 구분해서 알려주면 회원 번호를 알아내는 데 쓰일 수 있다).
 */
public record RevocationConfirmResponse(
        @JsonProperty("revoked") boolean revoked,
        @JsonProperty("revoked_count") int revokedCount
) {
}