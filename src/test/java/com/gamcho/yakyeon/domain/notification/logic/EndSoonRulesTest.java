package com.gamcho.yakyeon.domain.notification.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EndSoonRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final LocalDate STARTED = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("마지막 복용일이 오늘부터 3일 뒤 사이면 대상이다")
    void dueWindow() {
        assertThat(EndSoonRules.isDue(STARTED, TODAY.plusDays(3), TODAY)).as("3일 뒤(포함)").isTrue();
        assertThat(EndSoonRules.isDue(STARTED, TODAY.plusDays(1), TODAY)).as("내일").isTrue();
        assertThat(EndSoonRules.isDue(STARTED, TODAY, TODAY)).as("오늘 끝남(늦게 등록한 경우도 한 번은 알림)").isTrue();
        assertThat(EndSoonRules.isDue(STARTED, TODAY.plusDays(4), TODAY)).as("4일 뒤(아직 이르다)").isFalse();
        assertThat(EndSoonRules.isDue(STARTED, TODAY.minusDays(1), TODAY)).as("이미 끝남").isFalse();
    }

    @Test
    @DisplayName("오늘 막 시작한 짧은 처방에는 곧 끝난다고 알리지 않는다")
    void newShortPrescriptionIsSkipped() {
        assertThat(EndSoonRules.isDue(TODAY, TODAY.plusDays(2), TODAY)).as("오늘 시작한 3일치").isFalse();
        assertThat(EndSoonRules.isDue(TODAY.minusDays(1), TODAY.plusDays(2), TODAY)).as("어제 시작").isTrue();
    }

    @Test
    @DisplayName("오전 9시 이후에만 알린다")
    void noticeTime() {
        assertThat(EndSoonRules.isNoticeTime(LocalTime.of(8, 59, 59))).isFalse();
        assertThat(EndSoonRules.isNoticeTime(LocalTime.of(9, 0))).isTrue();
        assertThat(EndSoonRules.isNoticeTime(LocalTime.of(23, 30))).isTrue();
        assertThat(EndSoonRules.isNoticeTime(LocalTime.of(0, 5))).isFalse();
    }

    @Test
    @DisplayName("SQL에 넘기는 끝 날짜는 판정 함수와 같은 경계다")
    void boundariesMatch() {
        assertThat(EndSoonRules.noticeUntil(TODAY)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(EndSoonRules.NOTICE_DAYS).isEqualTo(3);
    }
}