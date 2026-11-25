package com.gamcho.yakyeon.domain.dose.logic;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 처방 하나의 "있어야 하는 예정"을 계산한다 (DB·스프링 없는 순수 로직).
 *
 * 처방의 복용 시각(시간대별)과 약의 복용 기간·시간대로부터, 앞으로 며칠 동안의 예정을 만든다.
 * DB의 예정 목록 뷰(v_scheduled_dose_item)와 같은 기준이다:
 *  - 그 시간대에 먹는 약이 하나라도 있어야 한다 (약이 없는 시간대의 예정은 만들지 않는다)
 *  - 약의 복용 기간 안의 날짜여야 한다 (시작일·종료일 당일 포함)
 *  - 약 또는 처방을 종료한 시각이 예정 시각보다 뒤여야 한다 (종료 이후의 예정에는 그 약이 빠진다)
 *
 * 마감은 예정 시각 + 마감 분이지만 같은 날 23:59:59를 넘지 않게 자른다. 아침·점심·저녁은 20:59 이전이라 잘리지 않고,
 * 취침(최대 23:59)만 해당한다 (취침 22:00이면 마감 23:59:59). 자정을 넘는 예정은 만들지 않는다.
 */
public final class DosePlanner {

    private DosePlanner() {
    }

    /**
     * @param firstDay 첫 날(보통 오늘, 한국 날짜)
     * @param days     며칠치를 만들지 (firstDay 포함)
     */
    public static List<PlannedDose> plan(List<SettingRef> settings, List<MedWindow> meds,
                                         OffsetDateTime prescriptionEndedAt,
                                         LocalDate firstDay, int days, ZoneId zone) {
        List<PlannedDose> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = firstDay.plusDays(i);
            OffsetDateTime endOfDay = ZonedDateTime.of(date, LocalTime.of(23, 59, 59), zone).toOffsetDateTime();
            for (SettingRef s : settings) {
                OffsetDateTime scheduledAt = ZonedDateTime.of(date, s.doseTime(), zone).toOffsetDateTime();
                if (prescriptionEndedAt != null && !prescriptionEndedAt.isAfter(scheduledAt)) {
                    continue;
                }
                boolean hasItem = meds.stream().anyMatch(m ->
                        m.slots().contains(s.slot())
                                && !date.isBefore(m.startDate()) && !date.isAfter(m.endDate())
                                && (m.endedAt() == null || m.endedAt().isAfter(scheduledAt)));
                if (hasItem) {
                    OffsetDateTime closeAt = scheduledAt.plusMinutes(s.closeAfterMin());
                    result.add(new PlannedDose(s.slot(), date, s.settingId(), scheduledAt,
                            scheduledAt.plusMinutes(s.normalWindowMin()),
                            closeAt.isAfter(endOfDay) ? endOfDay : closeAt));
                }
            }
        }
        result.sort(Comparator.comparing(PlannedDose::scheduledAt));
        return result;
    }
}