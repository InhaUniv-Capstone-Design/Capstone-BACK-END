package com.gamcho.yakyeon.domain.interaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 홈 화면 요약.
 *
 * @param total     오늘 복용 중인 약 수
 * @param flagged   병용금기로 등재된 약 쌍 수
 * @param unchecked 약물 DB와 매칭되지 않아 병용금기를 확인하지 못한 약 수.
 *                  0이 아니면 "등재된 정보 없음"을 안전하다는 뜻으로 보여주면 안 된다 - 화면에서 "확인하지 못한 약이 있어요"를 함께 알려야 한다.
 */
public record InteractionSummaryResponse(
        @JsonProperty("total") int total,
        @JsonProperty("flagged") int flagged,
        @JsonProperty("unchecked") int unchecked
) {
}