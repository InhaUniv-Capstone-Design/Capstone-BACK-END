package com.gamcho.yakyeon.domain.schedule.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduleRulesTest {

    private static Map<String, LocalTime> times(String... pairs) {
        Map<String, LocalTime> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], LocalTime.parse(pairs[i + 1]));
        }
        return map;
    }

    private static String violation(Map<String, LocalTime> times, String... required) {
        return ScheduleRules.firstViolation(times, Set.of(required)).orElse(null);
    }

    // ---------------------------------------------------------------- 취침 추가

    @Test
    @DisplayName("아침·점심·저녁·취침 네 시간대를 모두 설정할 수 있다")
    void fourSlotsAreAllowed() {
        Map<String, LocalTime> t = times("MORNING", "08:00", "LUNCH", "12:00", "DINNER", "18:00", "BEDTIME", "22:00");
        assertThat(violation(t, "MORNING", "LUNCH", "DINNER", "BEDTIME")).isEqualTo(null);
    }

    @Test
    @DisplayName("취침만 쓰는 처방도 설정할 수 있다")
    void bedtimeOnly() {
        assertThat(violation(times("BEDTIME", "23:00"), "BEDTIME")).isEqualTo(null);
    }

    @Test
    @DisplayName("취침을 쓰는 약인데 취침 시각이 없으면 입력하라고 알려준다")
    void bedtimeTimeIsRequired() {
        assertThat(violation(times("MORNING", "08:00"), "MORNING", "BEDTIME")).isEqualTo("취침 복용 시각을 입력해주세요.");
        assertThat(violation(times("MORNING", "08:00"), "MORNING", "DINNER", "BEDTIME")).isEqualTo("저녁, 취침 복용 시각을 입력해주세요.");
    }

    @Test
    @DisplayName("가장 늦은 시각: 아침·점심·저녁은 20:59, 취침은 23:59")
    void latestTimes() {
        assertThat(ScheduleRules.latestDoseTime("MORNING")).isEqualTo(LocalTime.of(20, 59));
        assertThat(ScheduleRules.latestDoseTime("LUNCH")).isEqualTo(LocalTime.of(20, 59));
        assertThat(ScheduleRules.latestDoseTime("DINNER")).isEqualTo(LocalTime.of(20, 59));
        assertThat(ScheduleRules.latestDoseTime("BEDTIME")).isEqualTo(LocalTime.of(23, 59));
        assertThat(ScheduleRules.latestDoseTime()).as("기존 호출 호환").isEqualTo(LocalTime.of(20, 59));
    }

    @Test
    @DisplayName("취침은 23:59까지 가능하고, 아침·점심·저녁은 21:00부터 안 된다")
    void latestTimeBoundaries() {
        assertThat(violation(times("BEDTIME", "23:59"), "BEDTIME")).isEqualTo(null);
        assertThat(violation(times("DINNER", "20:59"), "DINNER")).isEqualTo(null);
        assertThat(violation(times("DINNER", "21:00"), "DINNER")).isEqualTo("저녁 복용 시각은 20:59 이전으로 설정해주세요.");
        assertThat(violation(times("MORNING", "21:00"), "MORNING")).isEqualTo("아침 복용 시각은 20:59 이전으로 설정해주세요.");
        assertThat(violation(times("LUNCH", "23:00"), "LUNCH")).isEqualTo("점심 복용 시각은 20:59 이전으로 설정해주세요.");
    }

    @Test
    @DisplayName("취침은 저녁보다 뒤여야 한다")
    void bedtimeMustComeAfterDinner() {
        assertThat(violation(times("DINNER", "18:00", "BEDTIME", "17:00"), "DINNER", "BEDTIME"))
                .isEqualTo("아침, 점심, 저녁, 취침 순서로 시각을 설정해주세요.");
        assertThat(violation(times("DINNER", "18:00", "BEDTIME", "18:00"), "DINNER", "BEDTIME"))
                .as("같은 시각").isEqualTo("아침, 점심, 저녁, 취침 순서로 시각을 설정해주세요.");
    }

    @Test
    @DisplayName("취침도 앞 시간대와 4시간 이상 떨어져야 한다 (정확히 4시간은 가능)")
    void bedtimeNeedsFourHourGap() {
        assertThat(violation(times("DINNER", "19:00", "BEDTIME", "22:30"), "DINNER", "BEDTIME"))
                .isEqualTo("저녁과 취침 복용 시각은 최소 4시간 이상 간격을 두어야 합니다.");
        assertThat(violation(times("DINNER", "18:00", "BEDTIME", "22:00"), "DINNER", "BEDTIME")).as("정확히 4시간").isEqualTo(null);
        assertThat(violation(times("DINNER", "18:00", "BEDTIME", "21:59"), "DINNER", "BEDTIME")).as("3시간 59분").isEqualTo(
                "저녁과 취침 복용 시각은 최소 4시간 이상 간격을 두어야 합니다.");
    }

    @Test
    @DisplayName("저녁 없이 아침·취침만 있어도 아침과 취침 사이를 본다")
    void gapSkipsMissingSlots() {
        assertThat(violation(times("MORNING", "08:00", "BEDTIME", "11:00"), "MORNING", "BEDTIME"))
                .isEqualTo("아침과 취침 복용 시각은 최소 4시간 이상 간격을 두어야 합니다.");
        assertThat(violation(times("MORNING", "08:00", "BEDTIME", "12:00"), "MORNING", "BEDTIME")).isEqualTo(null);
    }

    // ---------------------------------------------------------------- 마감과 정상 인정 시간

    @Test
    @DisplayName("마감까지의 분: 보통 180분이고 취침은 같은 날 23:59:59를 넘지 않게 잘린다")
    void closeAfterMinutes() {
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(8, 0))).isEqualTo(180);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(20, 59))).as("일반 시간대의 가장 늦은 시각").isEqualTo(180);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(21, 0))).isEqualTo(180);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(21, 1))).isEqualTo(179);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(22, 0))).as("명세의 예: 취침 22:00 → 마감 23:59:59").isEqualTo(120);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(23, 0))).isEqualTo(60);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(23, 30))).isEqualTo(30);
        assertThat(ScheduleRules.closeAfterMin(LocalTime.of(23, 59))).isEqualTo(1);
    }

    @Test
    @DisplayName("정상 인정 시간: 보통 30분이고 마감 안쪽이어야 해서 늦은 취침은 줄어든다")
    void normalWindowMinutes() {
        assertThat(ScheduleRules.normalWindowMin(LocalTime.of(8, 0))).isEqualTo(30);
        assertThat(ScheduleRules.normalWindowMin(LocalTime.of(22, 0))).isEqualTo(30);
        assertThat(ScheduleRules.normalWindowMin(LocalTime.of(23, 29))).isEqualTo(30);
        assertThat(ScheduleRules.normalWindowMin(LocalTime.of(23, 30))).as("마감 30분 → 정상은 29분").isEqualTo(29);
        assertThat(ScheduleRules.normalWindowMin(LocalTime.of(23, 59))).as("마감 1분 → 정상 0분").isEqualTo(0);
    }

    @Test
    @DisplayName("기존 동작은 그대로: 20:59 이전의 모든 시각은 정상 30분, 마감 180분이다")
    void existingBehaviourIsUnchanged() {
        for (int minute = 0; minute <= 20 * 60 + 59; minute++) {
            LocalTime time = LocalTime.of(minute / 60, minute % 60);
            assertThat(ScheduleRules.closeAfterMin(time)).as(time + " 마감").isEqualTo(180);
            assertThat(ScheduleRules.normalWindowMin(time)).as(time + " 정상").isEqualTo(30);
        }
    }

    @Test
    @DisplayName("하루 어느 시각이든 DB 제약을 지킨다: 마감 > 정상 인정 >= 0, 둘 다 SMALLINT 범위")
    void dbConstraintsHoldForEveryMinuteOfTheDay() {
        for (int minute = 0; minute < 24 * 60; minute++) {
            LocalTime time = LocalTime.of(minute / 60, minute % 60);
            int close = ScheduleRules.closeAfterMin(time);
            int normal = ScheduleRules.normalWindowMin(time);
            assertThat(normal >= 0).as(time + " 정상 0 이상").isTrue();
            assertThat(close > normal).as(time + " 마감이 정상보다 큼(" + close + ">" + normal + ")").isTrue();
            assertThat(close <= 180).as(time + " 마감 180 이하").isTrue();
            // 마감 시각이 같은 날 안에 있다 (분 단위로 더해도 24:00을 넘으면 DosePlanner가 23:59:59로 자른다)
            assertThat(time.toSecondOfDay() + normal * 60 < 24 * 60 * 60).as(time + " 정상 인정이 자정 전").isTrue();
        }
    }
}