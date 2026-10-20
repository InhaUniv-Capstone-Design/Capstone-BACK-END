package com.gamcho.yakyeon.domain.interaction.logic;

/** 복약자의 약 두 개 사이의 병용금기 한 건. 약 번호는 항상 medicationAId < medicationBId. */
public record Finding(long medicationAId, long medicationBId, String durSeq,
                      String ingredientACode, String ingredientBCode,
                      String typeCode, String reason, String remark, String sourceName) {
}