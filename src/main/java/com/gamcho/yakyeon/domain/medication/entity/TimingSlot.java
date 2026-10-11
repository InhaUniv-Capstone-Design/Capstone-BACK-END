package com.gamcho.yakyeon.domain.medication.entity;

/**
 * 복용 시기. DB의 CHECK 제약(MORNING, LUNCH, DINNER)과 정확히 같아야 한다.
 * medication_timing, schedule_setting, scheduled_dose, intake_event가 같은 값을 쓴다.
 */
public enum TimingSlot {
    MORNING,  // 아침
    LUNCH,    // 점심
    DINNER    // 저녁
}