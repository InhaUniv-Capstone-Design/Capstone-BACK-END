package com.gamcho.yakyeon.domain.medication.entity;

/**
 * 복용 시기. DB의 CHECK 제약(MORNING, LUNCH, DINNER, BEDTIME)과 정확히 같아야 한다.
 * 선언 순서가 곧 하루의 순서(아침 → 점심 → 저녁 → 취침)다. 정렬에 이 순서를 쓰는 곳이 있어서 바꾸면 안 된다.
 * medication_timing, schedule_setting, scheduled_dose, intake_event가 같은 값을 쓴다.
 */
public enum TimingSlot {
    MORNING,  // 아침
    LUNCH,    // 점심
    DINNER,   // 저녁
    BEDTIME   // 취침
}