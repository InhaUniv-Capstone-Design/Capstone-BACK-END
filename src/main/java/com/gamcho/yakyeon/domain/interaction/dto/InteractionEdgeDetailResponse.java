package com.gamcho.yakyeon.domain.interaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** 그래프의 연결선(엣지)을 눌렀을 때 보이는 상세. 면책 문구는 항상 포함된다. */
public record InteractionEdgeDetailResponse(
        @JsonProperty("reason_text") String reasonText,
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("source_base_date") LocalDate sourceBaseDate,
        @JsonProperty("precaution") String precaution,
        @JsonProperty("disclaimer") String disclaimer
) {
}