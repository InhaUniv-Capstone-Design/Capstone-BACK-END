package com.gamcho.yakyeon.domain.medication.logic;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * "복용 종료"(ended_at) 관련 날짜 계산. 스프링·DB 없는 순수 로직이라 단독으로 시험할 수 있다.
 *
 * ended_at은 사용자가 종료를 누른 "순간"이다. DB는 그 순간 이후의 예정에서만 약을 뺀다
 * (그날 이미 지난 시간대의 기록은 그대로). 목록 화면은 날짜 단위로 상태를 보여주므로,
 * 종료를 누른 한국 날짜부터는 종료된 약으로 본다.
 */
public final class MedicationEnding {

    private MedicationEnding() {
    }

    /**
     * 약과 처방 중 먼저 종료된 쪽의 한국 날짜. 둘 다 종료되지 않았으면 null.
     * 처방 전체를 종료하면 그 안의 모든 약이 함께 종료된 것으로 본다.
     */
    public static LocalDate endedOn(OffsetDateTime medicationEndedAt, OffsetDateTime prescriptionEndedAt, ZoneId zone) {
        OffsetDateTime earliest = medicationEndedAt;
        if (prescriptionEndedAt != null && (earliest == null || prescriptionEndedAt.isBefore(earliest))) {
            earliest = prescriptionEndedAt;
        }
        return earliest == null ? null : earliest.atZoneSameInstant(zone).toLocalDate();
    }

    /** 기준일이 종료일 당일이거나 그 뒤면 종료된 상태다. 종료일 전의 날짜는 당시 그대로 복용 중이었다. */
    public static boolean isEndedOn(LocalDate date, LocalDate endedOn) {
        return endedOn != null && !date.isBefore(endedOn);
    }
}