package com.gamcho.yakyeon.domain.dose.logic;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * 일정을 만들 때 필요한 약 정보. 삭제된 약과 필요 시 복용(PRN) 약은 호출하는 쪽에서 이미 뺀다.
 *
 * @param slots   이 약이 복용하는 시간대 (MORNING/LUNCH/DINNER)
 * @param endedAt 사용자가 "복용 종료"를 누른 시각. 없으면 null
 */
public record MedWindow(Set<String> slots, LocalDate startDate, LocalDate endDate, OffsetDateTime endedAt) {
}