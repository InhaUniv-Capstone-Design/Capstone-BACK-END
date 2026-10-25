package com.gamcho.yakyeon.domain.dose.logic;

import java.time.OffsetDateTime;

/** DB에 이미 있는 예정 한 건. pending은 아직 판정되지 않은(PENDING) 예정이다 */
public record ExistingDose(long doseId, String slot, OffsetDateTime scheduledAt, long settingId, boolean pending) {
}