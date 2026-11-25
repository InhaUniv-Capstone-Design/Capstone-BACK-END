package com.gamcho.yakyeon.domain.interaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * 병용금기 목록 한 건. 등급·점수 같은 위험도 필드는 없다 (서비스 정책: 등재 여부만 안내).
 * 약 이름은 사용자가 확인해서 등록한 이름이다.
 */
public record InteractionItemResponse(
        @JsonProperty("medication_a_id") Long medicationAId,
        @JsonProperty("medication_a_name") String medicationAName,
        @JsonProperty("medication_b_id") Long medicationBId,
        @JsonProperty("medication_b_name") String medicationBName,
        @JsonProperty("dur_seq") String durSeq,
        @JsonProperty("reason_text") String reasonText,
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("source_base_date") LocalDate sourceBaseDate
) {
}