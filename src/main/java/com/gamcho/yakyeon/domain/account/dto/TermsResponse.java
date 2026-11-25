package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /terms 응답 항목 1건 = 약관 종류 하나의 "현재 시행 중인 최신 버전".
 * 회원가입 요청의 agreements[].terms_version_id에는 여기서 받은 terms_version_id를 그대로 넣는다.
 *
 * effective_at(시행 시각)은 의도적으로 뺐다 - 프론트가 화면에 쓸 값이 아니고,
 * LocalDateTime 타임존 직렬화 방침이 아직 확정되지 않아서 노출을 미뤘다.
 */
public record TermsResponse(
        @JsonProperty("terms_version_id") Long termsVersionId,
        @JsonProperty("terms_type") String termsType,
        @JsonProperty("version") String version,
        @JsonProperty("content") String content,
        @JsonProperty("is_required") Boolean isRequired
) {
}