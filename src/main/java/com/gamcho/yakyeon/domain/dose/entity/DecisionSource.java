package com.gamcho.yakyeon.domain.dose.entity;

/** 누가 판정했는지. DB의 CHECK 제약(DEVICE, MANUAL, SYSTEM)과 정확히 같아야 한다. */
public enum DecisionSource {
    DEVICE,   // 약통(칸 열림 + 버튼)
    MANUAL,   // 앱에서 직접 기록·정정
    SYSTEM    // 마감이 지나서 시스템이 누락으로 확정
}