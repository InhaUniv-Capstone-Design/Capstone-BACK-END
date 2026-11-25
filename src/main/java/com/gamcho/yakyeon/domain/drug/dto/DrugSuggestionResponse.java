package com.gamcho.yakyeon.domain.drug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 약물명 자동완성 항목. 같은 이름의 제품이 업체별로 여러 개 있어서 manufacturer를 함께 내려준다.
 * drug_id는 처방 등록에서 약을 확정할 때 쓰는 값이다.
 */
public record DrugSuggestionResponse(
        @JsonProperty("drug_id") Long drugId,
        @JsonProperty("product_name") String productName,
        @JsonProperty("manufacturer") String manufacturer
) {
}