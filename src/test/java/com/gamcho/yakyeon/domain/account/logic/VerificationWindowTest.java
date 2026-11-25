package com.gamcho.yakyeon.domain.account.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationWindowTest {

    private static final LocalDateTime REQUESTED = LocalDateTime.parse("2026-11-03T10:00:00");

    private static int minutesAfter(long seconds) {
        return VerificationWindow.withinMinutes(REQUESTED, REQUESTED.plusSeconds(seconds));
    }

    @Test
    @DisplayName("요청 직후나 1분 이내는 1분이다")
    void atLeastOneMinute() {
        assertThat(minutesAfter(0)).isEqualTo(1);
        assertThat(minutesAfter(1)).isEqualTo(1);
        assertThat(minutesAfter(60)).isEqualTo(1);
    }

    @Test
    @DisplayName("경과 시간은 분으로 올림한다 (61초는 2분)")
    void roundsUp() {
        assertThat(minutesAfter(61)).isEqualTo(2);
        assertThat(minutesAfter(120)).isEqualTo(2);
        assertThat(minutesAfter(121)).isEqualTo(3);
    }

    @Test
    @DisplayName("인증번호 유효시간(5분) 안에서는 최대 5분이다")
    void withinTheCodeLifetime() {
        assertThat(minutesAfter(300)).isEqualTo(5);
    }

    @Test
    @DisplayName("OCTOMO가 받는 최대 60분을 넘지 않는다")
    void cappedAtSixtyMinutes() {
        assertThat(minutesAfter(3600)).isEqualTo(60);
        assertThat(minutesAfter(3601)).isEqualTo(60);
        assertThat(minutesAfter(86_400)).isEqualTo(60);
    }

    @Test
    @DisplayName("서버 시계가 뒤로 가서 경과가 음수여도 1분이다")
    void negativeElapsed() {
        assertThat(VerificationWindow.withinMinutes(REQUESTED, REQUESTED.minusSeconds(30))).isEqualTo(1);
    }
}