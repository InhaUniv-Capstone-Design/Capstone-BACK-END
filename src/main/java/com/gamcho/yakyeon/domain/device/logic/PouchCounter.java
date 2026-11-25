package com.gamcho.yakyeon.domain.device.logic;

import com.gamcho.yakyeon.domain.dose.logic.MedWindow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 처방(약봉투)에 앞으로 필요한 약봉지 수를 센다 (스프링·DB 없는 순수 로직).
 *
 * 약봉지는 복용 한 번에 한 개라서, 오늘부터 약의 복용이 끝나는 날까지 "그날 복용하는 시간대의 수"를 날마다 더한 값이다.
 * 약들의 기간이 같고 시간대도 같다면 "복용 시간대 수 × 총 투약일수"와 같은 값이다. 약마다 기간이나 시간대가 다르면 그 차이를 반영한다
 * (예: 약 A 아침 7일, 약 B 저녁 3일이면 7 + 3 = 10개. 단순 곱셈은 2 × 7 = 14개로 많이 센다).
 *
 * 복용 시각(schedule_setting)을 정하기 전에도 셀 수 있도록 약이 정한 시간대와 기간만 쓴다(복용 일정 계산기를 쓰면 시각이 없을 때 0이 나온다).
 * 오늘의 시간대는 이미 지난 것도 센다: 조금 많이 세는 쪽이 모자라게 세는 쪽(남은 수가 0 밑으로 내려가려는 상황)보다 안전하다.
 */
public final class PouchCounter {

    /** 자동 계산의 상한. 기간이 비정상적으로 길어도 터무니없는 수가 되지 않게 한다 (365일 × 4시간대 = 1460) */
    public static final int MAX_POUCHES = 1500;

    private static final int MAX_DAYS = 366;

    private PouchCounter() {
    }

    /**
     * @param meds  이 처방의 약 (삭제된 약과 필요 시 복용(PRN) 약은 호출하는 쪽에서 이미 뺀다)
     * @param today 오늘 (한국 날짜). 이날부터 센다
     */
    public static int remaining(List<MedWindow> meds, LocalDate today, ZoneId zone) {
        LocalDate last = meds.stream().map(MedWindow::endDate).max(LocalDate::compareTo).orElse(null);
        if (last == null || last.isBefore(today)) {
            return 0;
        }
        int days = (int) Math.min(ChronoUnit.DAYS.between(today, last) + 1, MAX_DAYS);
        int count = 0;
        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);
            Set<String> slots = new HashSet<>();
            for (MedWindow m : meds) {
                boolean inPeriod = !date.isBefore(m.startDate()) && !date.isAfter(m.endDate());
                boolean notEnded = m.endedAt() == null || m.endedAt().atZoneSameInstant(zone).toLocalDate().isAfter(date);
                if (inPeriod && notEnded) {
                    slots.addAll(m.slots());
                }
            }
            count += slots.size();
        }
        return Math.min(count, MAX_POUCHES);
    }
}