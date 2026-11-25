package com.gamcho.yakyeon.domain.dose.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HomeSummaryCalculatorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private static OffsetDateTime at(String time) {
        return ZonedDateTime.of(DAY, LocalTime.parse(time), KST).toOffsetDateTime();
    }

    private static DoseBrief dose(long id, String slot, String status, String scheduled, String close) {
        return new DoseBrief(id, slot, status, at(scheduled), at(close));
    }

    private static String stateOf(HomeSummaryCalculator.Summary summary, String slot) {
        return summary.slots().stream().filter(s -> s.slot().equals(slot)).findFirst().orElseThrow().state();
    }

    @Test
    @DisplayName("시간대 아이콘은 항상 4개이고 아침 → 점심 → 저녁 → 취침 순서다")
    void alwaysFourSlotsInOrder() {
        HomeSummaryCalculator.Summary empty = HomeSummaryCalculator.compute(List.of(), at("09:00"));
        assertThat(empty.slots().size()).isEqualTo(4);
        assertThat(empty.slots().get(0).slot()).isEqualTo("MORNING");
        assertThat(empty.slots().get(1).slot()).isEqualTo("LUNCH");
        assertThat(empty.slots().get(2).slot()).isEqualTo("DINNER");
        assertThat(empty.slots().get(3).slot()).isEqualTo("BEDTIME");
        for (HomeSummaryCalculator.SlotState s : empty.slots()) {
            assertThat(s.state()).as(s.slot()).isEqualTo("NONE");
        }
        assertThat(empty.total()).isEqualTo(0);
    }

    @Test
    @DisplayName("취침 예정이 있으면 취침 아이콘이 복용 상태를 따라간다")
    void bedtimeStates() {
        OffsetDateTime now = at("21:00");
        HomeSummaryCalculator.Summary pending = HomeSummaryCalculator.compute(
                List.of(dose(1, "BEDTIME", "PENDING", "22:00", "23:59")), now);
        assertThat(stateOf(pending, "BEDTIME")).isEqualTo("PENDING");

        HomeSummaryCalculator.Summary done = HomeSummaryCalculator.compute(
                List.of(dose(1, "BEDTIME", "NORMAL", "22:00", "23:59")), at("22:10"));
        assertThat(stateOf(done, "BEDTIME")).isEqualTo("DONE");

        HomeSummaryCalculator.Summary delayed = HomeSummaryCalculator.compute(
                List.of(dose(1, "BEDTIME", "DELAYED", "22:00", "23:59")), at("23:00"));
        assertThat(stateOf(delayed, "BEDTIME")).isEqualTo("DONE");

        HomeSummaryCalculator.Summary missed = HomeSummaryCalculator.compute(
                List.of(dose(1, "BEDTIME", "MISSED", "22:00", "23:59")), at("23:59"));
        assertThat(stateOf(missed, "BEDTIME")).isEqualTo("MISSED");
    }

    @Test
    @DisplayName("집계에 취침 예정도 포함된다 (복약 현황 2/4)")
    void countsIncludeBedtime() {
        HomeSummaryCalculator.Summary s = HomeSummaryCalculator.compute(List.of(
                dose(1, "MORNING", "NORMAL", "08:00", "11:00"),
                dose(2, "LUNCH", "DELAYED", "12:00", "15:00"),
                dose(3, "DINNER", "MISSED", "18:00", "21:00"),
                dose(4, "BEDTIME", "PENDING", "22:00", "23:59")), at("22:05"));
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.done()).isEqualTo(2);
        assertThat(s.normal()).isEqualTo(1);
        assertThat(s.delayed()).isEqualTo(1);
        assertThat(s.missed()).isEqualTo(1);
        assertThat(s.pending()).isEqualTo(1);
        assertThat(stateOf(s, "MORNING")).isEqualTo("DONE");
        assertThat(stateOf(s, "DINNER")).isEqualTo("MISSED");
        assertThat(stateOf(s, "BEDTIME")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("다음 복약: 저녁을 마친 뒤에는 취침이 다음이다")
    void nextDoseIsBedtimeAfterDinner() {
        List<DoseBrief> doses = List.of(
                dose(3, "DINNER", "NORMAL", "18:00", "21:00"),
                dose(4, "BEDTIME", "PENDING", "22:00", "23:59"));
        assertThat(HomeSummaryCalculator.compute(doses, at("21:30")).nextDoseId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("다음 복약: 취침의 마감(23:59)이 지나면 다음이 없다")
    void noNextDoseAfterBedtimeCloses() {
        List<DoseBrief> doses = List.of(dose(4, "BEDTIME", "PENDING", "22:00", "23:59"));
        assertThat(HomeSummaryCalculator.compute(doses, at("23:59")).nextDoseId() == null).isTrue();
    }

    @Test
    @DisplayName("기존 동작: 취침 예정이 없으면 취침은 NONE이고 나머지 계산은 그대로다")
    void existingSlotsUnchanged() {
        HomeSummaryCalculator.Summary s = HomeSummaryCalculator.compute(List.of(
                dose(1, "MORNING", "NORMAL", "08:00", "11:00"),
                dose(2, "LUNCH", "PENDING", "12:00", "15:00")), at("12:05"));
        assertThat(stateOf(s, "MORNING")).isEqualTo("DONE");
        assertThat(stateOf(s, "LUNCH")).isEqualTo("PENDING");
        assertThat(stateOf(s, "DINNER")).isEqualTo("NONE");
        assertThat(stateOf(s, "BEDTIME")).isEqualTo("NONE");
        assertThat(s.nextDoseId()).isEqualTo(2L);
    }
}