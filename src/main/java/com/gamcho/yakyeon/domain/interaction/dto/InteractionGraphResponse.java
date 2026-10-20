package com.gamcho.yakyeon.domain.interaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 약물 관계 그래프. groups는 병용금기로 서로 이어진 약 묶음이며 위험 수준이 아니라 구조만 보여준다.
 * nodes에는 끝나지 않은 모든 약이 들어가고, 약물 DB와 매칭되지 않은 약은 checked=false다.
 */
public record InteractionGraphResponse(
        @JsonProperty("nodes") List<Node> nodes,
        @JsonProperty("edges") List<Edge> edges,
        @JsonProperty("groups") List<List<Long>> groups
) {
    public record Node(
            @JsonProperty("medication_id") Long medicationId,
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("checked") boolean checked
    ) {
    }

    public record Edge(
            @JsonProperty("med_a") Long medA,
            @JsonProperty("med_b") Long medB,
            @JsonProperty("dur_seq") String durSeq,
            @JsonProperty("reason_text") String reasonText,
            @JsonProperty("source_name") String sourceName
    ) {
    }
}