package com.gamcho.yakyeon.domain.interaction.logic;

import java.time.LocalDate;

/**
 * 병용금기 검사에 필요한 약 정보만 담은 값 객체 (엔티티와 분리해서 로직을 단순하게 유지).
 *
 * @param itemSeq 식약처 품목기준코드. 약물 DB와 매칭되지 않은 약(UNMATCHED)은 null이고, 이 약은 검사할 수 없다.
 */
public record MedicationRef(long medicationId, String name, String itemSeq, LocalDate startDate, LocalDate endDate) {

    /** 약물 DB와 매칭돼서 병용금기를 확인할 수 있는 약인지 */
    public boolean checkable() {
        return itemSeq != null;
    }

    /** 기준일에 복용 중인지 (시작일과 종료일 포함) */
    public boolean activeOn(LocalDate date) {
        return !startDate.isAfter(date) && !endDate.isBefore(date);
    }
}