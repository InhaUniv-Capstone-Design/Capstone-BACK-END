package com.gamcho.yakyeon.domain.dose.logic;

import java.time.LocalTime;

/** 처방의 현재 유효한 복용 시각 한 건 (schedule_setting 한 행). slot은 MORNING/LUNCH/DINNER */
public record SettingRef(long settingId, String slot, LocalTime doseTime, int normalWindowMin, int closeAfterMin) {
}