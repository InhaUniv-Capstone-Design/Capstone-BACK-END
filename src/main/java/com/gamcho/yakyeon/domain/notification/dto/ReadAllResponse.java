package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 모두 읽음 처리한 결과 */
public record ReadAllResponse(@JsonProperty("updated_count") int updatedCount) {
}