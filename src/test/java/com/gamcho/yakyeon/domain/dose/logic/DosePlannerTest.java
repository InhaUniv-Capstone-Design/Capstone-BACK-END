package com.gamcho.yakyeon.domain.dose.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.schedule.logic.ScheduleRules;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DosePlannerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private static OffsetDateTime at(LocalDate date, String time) {
        return ZonedDateTime.of(date, LocalTime.parse(time), KST).toOffsetDateTime();
    }

    private static OffsetDateTime endOfDay(LocalDate date) {
        return ZonedDateTime.of(date, LocalTime.of(23, 59, 59), KST).toOffsetDateTime();
    }

    /** ScheduleSettingService가 저장하는 값과 같은 방식으로 설정을 만든다 */
    private static SettingRef setting(long id, String slot, String time) {
        LocalTime t = LocalTime.parse(time);
        return new SettingRef(id, slot, t, ScheduleRules.normalWindowMin(t), ScheduleRules.closeAfterMin(t));
    }

    private static MedWindow med(String... slots) {
        return new MedWindow(Set.of(slots), DAY.minusDays(1), DAY.plusDays(30), null);
    }

    private static List<PlannedDose> plan(List<SettingRef> settings, MedWindow med, int days) {
        return DosePlanner.plan(settings, List.of(med), null, DAY, days, KST);
    }

    @Test
    @DisplayName("취침 22:00: 정상 22:30까지, 마감은 같은 날 23:59:59 (명세의 예)")
    void bedtimeAtTen() {
        PlannedDose d = plan(List.of(setting(1, "BEDTIME", "22:00")), med("BEDTIME"), 1).get(0);
        assertThat(d.slot()).isEqualTo("BEDTIME");
        assertThat(d.scheduledAt()).isEqualTo(at(DAY, "22:00"));
        assertThat(d.normalUntil()).isEqualTo(at(DAY, "22:30"));
        assertThat(d.closeAt()).isEqualTo(endOfDay(DAY));
    }

    @Test
    @DisplayName("취침 23:59: 정상 인정 0분, 마감 23:59:59 (자정을 넘지 않고 DB 제약을 지킨다)")
    void bedtimeAtLastMinute() {
        PlannedDose d = plan(List.of(setting(1, "BEDTIME", "23:59")), med("BEDTIME"), 1).get(0);
        assertThat(d.normalUntil()).isEqualTo(d.scheduledAt());
        assertThat(d.closeAt()).isEqualTo(endOfDay(DAY));
        assertThat(d.normalUntil().isBefore(d.closeAt())).as("정상 < 마감").isTrue();
    }

    @Test
    @DisplayName("저장된 설정이 자정을 넘는 마감을 갖고 있어도(180분) 마감은 같은 날 23:59:59로 자른다")
    void closeIsCappedEvenForOldStyleSettings() {
        SettingRef oldStyle = new SettingRef(1, "BEDTIME", LocalTime.of(21, 30), 30, 180);   // 21:30 + 180분 = 다음 날 00:30
        PlannedDose d = plan(List.of(oldStyle), med("BEDTIME"), 1).get(0);
        assertThat(d.closeAt()).isEqualTo(endOfDay(DAY));
    }

    @Test
    @DisplayName("아침·점심·저녁은 그대로: 마감은 예정 180분 뒤이고 자르지 않는다 (저녁 20:59 → 23:59:00)")
    void regularSlotsAreUnchanged() {
        List<PlannedDose> doses = plan(List.of(setting(1, "MORNING", "08:00"), setting(2, "LUNCH", "12:00"),
                setting(3, "DINNER", "20:59")), med("MORNING", "LUNCH", "DINNER"), 1);
        assertThat(doses.size()).isEqualTo(3);
        assertThat(doses.get(0).closeAt()).isEqualTo(at(DAY, "11:00"));
        assertThat(doses.get(0).normalUntil()).isEqualTo(at(DAY, "08:30"));
        assertThat(doses.get(1).closeAt()).isEqualTo(at(DAY, "15:00"));
        assertThat(doses.get(2).closeAt()).isEqualTo(at(DAY, "23:59"));
    }

    @Test
    @DisplayName("네 시간대는 시각순으로 나오고, 취침이 마지막이다")
    void fourSlotsInOrder() {
        List<PlannedDose> doses = plan(List.of(setting(4, "BEDTIME", "22:00"), setting(1, "MORNING", "08:00"),
                setting(3, "DINNER", "18:00"), setting(2, "LUNCH", "12:00")), med("MORNING", "LUNCH", "DINNER", "BEDTIME"), 1);
        assertThat(doses.size()).isEqualTo(4);
        assertThat(doses.get(0).slot()).isEqualTo("MORNING");
        assertThat(doses.get(1).slot()).isEqualTo("LUNCH");
        assertThat(doses.get(2).slot()).isEqualTo("DINNER");
        assertThat(doses.get(3).slot()).isEqualTo("BEDTIME");
    }

    @Test
    @DisplayName("취침을 먹는 약이 없으면 취침 예정은 만들지 않는다")
    void noBedtimeDoseWithoutBedtimeMedication() {
        List<PlannedDose> doses = plan(List.of(setting(1, "MORNING", "08:00"), setting(4, "BEDTIME", "22:00")), med("MORNING"), 1);
        assertThat(doses.size()).isEqualTo(1);
        assertThat(doses.get(0).slot()).isEqualTo("MORNING");
    }

    @Test
    @DisplayName("며칠치를 만들어도 매일 마감이 그날 안에 있다 (자정을 넘는 예정이 없다)")
    void everyDayStaysInsideItsDay() {
        List<PlannedDose> doses = plan(List.of(setting(1, "DINNER", "18:00"), setting(4, "BEDTIME", "22:00")),
                med("DINNER", "BEDTIME"), 7);
        assertThat(doses.size()).isEqualTo(14);
        for (PlannedDose d : doses) {
            assertThat(d.closeAt().isAfter(endOfDay(d.date()))).as(d.slot() + " " + d.date() + " 마감이 자정 이후").isFalse();
            assertThat(!d.scheduledAt().isAfter(d.normalUntil()) && d.normalUntil().isBefore(d.closeAt()))
                    .as(d.slot() + " " + d.date() + " DB 제약 scheduled <= normal < close").isTrue();
        }
    }

    @Test
    @DisplayName("취침 예정 직전 하루 끝 시각 경계: 23:59:59 마감 1초 전 시각은 아직 마감 전이다")
    void closeAtIsInclusiveOfLastSecondOfDay() {
        PlannedDose d = plan(List.of(setting(1, "BEDTIME", "22:00")), med("BEDTIME"), 1).get(0);
        assertThat(d.closeAt().isAfter(at(DAY, "23:59"))).isTrue();
        assertThat(d.closeAt().isBefore(at(DAY.plusDays(1), "00:00"))).isTrue();
    }
}