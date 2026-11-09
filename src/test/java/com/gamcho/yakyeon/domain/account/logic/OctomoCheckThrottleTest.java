package com.gamcho.yakyeon.domain.account.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OctomoCheckThrottleTest {

    private static OctomoCheckThrottle throttle() {
        return new OctomoCheckThrottle(1500, 300_000);
    }

    @Test
    @DisplayName("처음 묻는 것은 허용한다")
    void firstAskIsAllowed() {
        assertThat(throttle().tooSoon(1L, 10_000)).isFalse();
    }

    @Test
    @DisplayName("간격(1.5초) 안에 다시 오면 막는다")
    void tooSoonIsBlocked() {
        OctomoCheckThrottle t = throttle();
        assertThat(t.tooSoon(1L, 10_000)).isFalse();
        assertThat(t.tooSoon(1L, 10_500)).isTrue();
        assertThat(t.tooSoon(1L, 11_499)).isTrue();
    }

    @Test
    @DisplayName("2초마다 확인하는 앱은 한 번도 막히지 않는다")
    void twoSecondPollingIsNeverBlocked() {
        OctomoCheckThrottle t = throttle();
        for (int i = 0; i < 150; i++) {
            assertThat(t.tooSoon(1L, 10_000 + i * 2_000L)).as("확인 " + i).isFalse();
        }
    }

    @Test
    @DisplayName("막힌 요청은 시각을 갱신하지 않는다 (잦게 부르는 앱이 계속 막히기만 하지 않고 1.5초 뒤에는 통과)")
    void blockedCallsDoNotPushTheClock() {
        OctomoCheckThrottle t = throttle();
        assertThat(t.tooSoon(1L, 10_000)).isFalse();
        assertThat(t.tooSoon(1L, 11_000)).isTrue();
        assertThat(t.tooSoon(1L, 11_400)).isTrue();
        assertThat(t.tooSoon(1L, 11_500)).as("마지막으로 허용한 10,000에서 1.5초 뒤").isFalse();
    }

    @Test
    @DisplayName("1초마다 부르는 앱은 두 번에 한 번만 OCTOMO까지 간다")
    void oneSecondPollingHalvesTheCalls() {
        OctomoCheckThrottle t = throttle();
        int allowed = 0;
        for (int i = 0; i < 100; i++) {
            if (!t.tooSoon(1L, 10_000 + i * 1_000L)) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(50);
    }

    @Test
    @DisplayName("인증마다 따로 센다")
    void verificationsAreIndependent() {
        OctomoCheckThrottle t = throttle();
        assertThat(t.tooSoon(1L, 10_000)).isFalse();
        assertThat(t.tooSoon(2L, 10_100)).isFalse();
        assertThat(t.tooSoon(1L, 10_200)).isTrue();
        assertThat(t.tooSoon(2L, 10_300)).isTrue();
    }

    @Test
    @DisplayName("끝난 인증의 기록은 지운다")
    void forgetClearsTheRecord() {
        OctomoCheckThrottle t = throttle();
        t.tooSoon(1L, 10_000);
        t.forget(1L);
        assertThat(t.sizeForTest()).isEqualTo(0);
        assertThat(t.tooSoon(1L, 10_100)).isFalse();
    }

    @Test
    @DisplayName("기록이 너무 많이 쌓이면 오래된 것을 정리한다 (메모리가 무한히 늘지 않게)")
    void prunesOldRecords() {
        OctomoCheckThrottle t = throttle();
        for (long i = 0; i < 5_001; i++) {
            t.tooSoon(i, 0);
        }
        assertThat(t.sizeForTest()).isEqualTo(5_001);
        t.tooSoon(9_999_999L, 400_000);
        assertThat(t.sizeForTest()).as("유효시간(300초)이 지난 기록이 정리되고 이번 것만 남음").isEqualTo(1);
    }
}