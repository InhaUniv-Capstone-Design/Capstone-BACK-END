package com.gamcho.yakyeon.domain.dose.logic;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 만들어야 하는 예정 한 건 (아직 DB에 없을 수 있다) */
public record PlannedDose(String slot, LocalDate date, long settingId,
                          OffsetDateTime scheduledAt, OffsetDateTime normalUntil, OffsetDateTime closeAt) {
}