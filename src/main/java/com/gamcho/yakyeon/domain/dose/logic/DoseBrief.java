package com.gamcho.yakyeon.domain.dose.logic;

import java.time.OffsetDateTime;

/** 홈 요약 계산에 필요한 예정의 최소 정보 (status는 PENDING/NORMAL/DELAYED/MISSED) */
public record DoseBrief(long doseId, String slot, String status, OffsetDateTime scheduledAt, OffsetDateTime closeAt) {
}