package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.drug.dto.DrugSuggestionResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * OCR이 읽어낸 약 한 줄과 약물 DB 매칭 결과 (확인용 초안, 저장되지 않음).
 *
 * - drug_name: 이미지에서 읽어낸 글자 그대로의 이름
 * - matched_drug_id: 이름이 약물 DB의 제품 하나와 정확히 일치할 때만 채워진다.
 *   같은 이름의 제품이 업체별로 여러 개면 자동으로 정할 수 없어 비워 두고, 사용자가 candidates에서 고르게 한다.
 * - candidates: 비슷한 이름의 제품 후보 (최대 3개). 글자를 잘못 읽은 경우 사용자가 올바른 약을 고르는 용도
 * 읽지 못한 항목은 null이다.
 */
public record RecognizedMedication(
        @JsonProperty("drug_name") String drugName,
        @JsonProperty("matched_drug_id") Long matchedDrugId,
        @JsonProperty("candidates") List<DrugSuggestionResponse> candidates,
        @JsonProperty("dose_per_intake") BigDecimal dosePerIntake,
        @JsonProperty("times_per_day") Short timesPerDay,
        @JsonProperty("total_days") Integer totalDays,
        @JsonProperty("is_prn") boolean prn,
        @JsonProperty("meal_relation") String mealRelation,
        @JsonProperty("meal_offset_min") Short mealOffsetMin
) {
}