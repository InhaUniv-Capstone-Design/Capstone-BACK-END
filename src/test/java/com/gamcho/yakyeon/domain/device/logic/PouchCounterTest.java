package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.dose.logic.MedWindow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PouchCounterTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    /** 오늘부터 days일 동안 복용하는 약 */
    private static MedWindow med(int days, String... slots) {
        return new MedWindow(Set.of(slots), TODAY, TODAY.plusDays(days - 1), null);
    }

    private static MedWindow medBetween(LocalDate start, LocalDate end, String... slots) {
        return new MedWindow(Set.of(slots), start, end, null);
    }

    @Test
    @DisplayName("기간과 시간대가 같으면 '복용 시간대 수 × 총 투약일수'와 같다 (3시간대 × 30일 = 90)")
    void sameAsSlotsTimesDays() {
        assertThat(PouchCounter.remaining(List.of(med(30, "MORNING", "LUNCH", "DINNER")), TODAY, ZONE)).isEqualTo(90);
        assertThat(PouchCounter.remaining(List.of(med(7, "MORNING", "DINNER")), TODAY, ZONE)).isEqualTo(14);
        assertThat(PouchCounter.remaining(List.of(med(5, "BEDTIME")), TODAY, ZONE)).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 처방의 약이 여러 개여도 같은 시간대는 한 봉지에 같이 든다 (약 개수와 상관없음)")
    void sameSlotMedsShareOnePouch() {
        List<MedWindow> meds = List.of(med(7, "MORNING", "DINNER"), med(7, "MORNING", "DINNER"), med(7, "DINNER"));
        assertThat(PouchCounter.remaining(meds, TODAY, ZONE)).isEqualTo(14);
    }

    @Test
    @DisplayName("약마다 기간이 다르면 단순 곱셈보다 정확하다: 아침 7일 + 저녁 3일 = 10 (단순 곱셈은 14)")
    void differentPeriodsAreNotMultiplied() {
        assertThat(PouchCounter.remaining(List.of(med(7, "MORNING"), med(3, "DINNER")), TODAY, ZONE)).isEqualTo(10);
    }

    @Test
    @DisplayName("한 약은 아침·저녁 7일, 다른 약은 저녁 3일이면 저녁 봉지는 7일 모두 필요하다 (7 + 7 = 14)")
    void overlappingSlotsCountOnce() {
        assertThat(PouchCounter.remaining(List.of(med(7, "MORNING", "DINNER"), med(3, "DINNER")), TODAY, ZONE)).isEqualTo(14);
    }

    @Test
    @DisplayName("이미 시작한 처방은 오늘부터 남은 날만 센다 (5일 전에 시작한 10일치면 오늘 포함 5일 남음)")
    void alreadyStartedCountsFromToday() {
        MedWindow started = medBetween(TODAY.minusDays(5), TODAY.plusDays(4), "MORNING", "DINNER");
        assertThat(PouchCounter.remaining(List.of(started), TODAY, ZONE)).isEqualTo(10);
    }

    @Test
    @DisplayName("앞으로 시작하는 처방은 시작일부터 센다 (이틀 뒤에 시작하는 3일치, 시간대 2개 = 6)")
    void futureStartCountsFromItsStart() {
        MedWindow future = medBetween(TODAY.plusDays(2), TODAY.plusDays(4), "MORNING", "DINNER");
        assertThat(PouchCounter.remaining(List.of(future), TODAY, ZONE)).isEqualTo(6);
    }

    @Test
    @DisplayName("오늘 끝나는 약은 오늘 몫만 센다")
    void endsToday() {
        assertThat(PouchCounter.remaining(List.of(medBetween(TODAY.minusDays(9), TODAY, "MORNING", "LUNCH", "DINNER")), TODAY, ZONE)).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 끝난 약, 약이 없는 처방, 필요 시 복용만 있는 처방(약 목록이 빔)은 0이다")
    void nothingLeftIsZero() {
        assertThat(PouchCounter.remaining(List.of(medBetween(TODAY.minusDays(10), TODAY.minusDays(1), "MORNING")), TODAY, ZONE)).isEqualTo(0);
        assertThat(PouchCounter.remaining(List.of(), TODAY, ZONE)).isEqualTo(0);
    }

    @Test
    @DisplayName("사용자가 '복용 종료'를 누른 약은 그 날 이후로 세지 않는다")
    void endedMedStopsCounting() {
        OffsetDateTime endedAt = TODAY.plusDays(2).atTime(9, 0).atZone(ZONE).toOffsetDateTime();   // 2일 뒤에 종료
        MedWindow ended = new MedWindow(Set.of("MORNING"), TODAY, TODAY.plusDays(9), endedAt);
        assertThat(PouchCounter.remaining(List.of(ended), TODAY, ZONE)).as("오늘과 내일만").isEqualTo(2);
    }

    @Test
    @DisplayName("기간이 비정상적으로 길어도 상한(1500)을 넘지 않는다")
    void capsAbsurdlyLongPeriods() {
        assertThat(PouchCounter.remaining(List.of(med(5000, "MORNING", "LUNCH", "DINNER", "BEDTIME")), TODAY, ZONE)).isEqualTo(1464);
        assertThat(PouchCounter.remaining(List.of(medBetween(TODAY, TODAY.plusYears(30), "MORNING", "LUNCH", "DINNER", "BEDTIME")), TODAY, ZONE) <= PouchCounter.MAX_POUCHES).isTrue();
    }
}