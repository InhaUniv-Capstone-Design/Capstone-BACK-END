package com.gamcho.yakyeon.domain.medication.entity;

/** 기준일에 대한 복용 기간 상태. DB에 저장되는 값이 아니라 시작일·종료일과 기준일로 계산한다. */
public enum MedicationStatus {
    UPCOMING,  // 복용 시작 전
    ACTIVE,    // 복용 중 (시작일과 종료일 포함)
    ENDED      // 복용 종료
}