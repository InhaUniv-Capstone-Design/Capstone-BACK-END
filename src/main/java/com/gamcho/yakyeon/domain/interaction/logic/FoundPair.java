package com.gamcho.yakyeon.domain.interaction.logic;

/** DUR 데이터에서 찾은 "제품 쌍 + 규칙" 한 건. 품목코드는 항상 itemSeqA < itemSeqB 순서(DB 제약). */
public record FoundPair(String itemSeqA, String itemSeqB, String durSeq,
                        String ingredientCodeA, String ingredientCodeB,
                        String typeCode, String reason, String remark, String sourceName) {
}