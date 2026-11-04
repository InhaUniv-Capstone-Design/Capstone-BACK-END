package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 열람 범위 변경 결과 (PATCH /guardian-links/{linkId}/shared-items). */
public record SharedItemsResponse(
        @JsonProperty("link_id") Long linkId,
        @JsonProperty("status") String status,
        @JsonProperty("shared_items") List<String> sharedItems
) {
}