package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 약관 동의 요청 (POST /users/me/agreements). 동의 철회는 별도 API를 쓴다. */
@Getter
@NoArgsConstructor
public class AgreementRequest {

    @NotNull(message = "약관 버전을 보내주세요.")
    @JsonProperty("terms_version_id")
    private Long termsVersionId;

    @NotNull(message = "동의 여부를 보내주세요.")
    @JsonProperty("agreed")
    private Boolean agreed;
}