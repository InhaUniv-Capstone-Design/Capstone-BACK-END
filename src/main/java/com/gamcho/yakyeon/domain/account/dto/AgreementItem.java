package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * 회원가입 요청에 포함되는 약관별 동의 항목 1건.
 * termsVersionId는 클라이언트가 "동의 화면에 표시된" 버전 ID를 그대로 되돌려 보내는 값이고,
 * 서버는 이걸 무조건 신뢰하지 않고 실제 최신 시행 버전과 일치하는지 다시 검증한다
 * (오래된 화면을 들고 있다가 구버전 약관에 동의해버리는 경우 방지).
 */
public record AgreementItem(

        @JsonProperty("terms_version_id")
        @NotNull(message = "약관 버전 ID가 필요합니다.")
        Long termsVersionId,

        @NotNull(message = "동의 여부(agreed)가 필요합니다.")
        Boolean agreed
) {
}