package com.gamcho.yakyeon.domain.dose.entity;

/** 예정의 판정 상태. DB의 CHECK 제약(PENDING, NORMAL, DELAYED, MISSED)과 정확히 같아야 한다. */
public enum DoseStatus {
    PENDING,   // 아직 판정 전
    NORMAL,    // 정상 복용
    DELAYED,   // 지연 복용
    MISSED     // 누락
}