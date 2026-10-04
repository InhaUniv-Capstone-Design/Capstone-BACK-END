package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 문자 인증 철회 1단계 - 인증번호 요청. 공개 API(로그인 불필요).
 * 형식 검증은 하이픈 등을 정규화한 뒤 서비스에서 한다.
 */
@Getter
public class RevocationRequestBody {

    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    private String phone;
}