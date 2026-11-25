package com.gamcho.yakyeon.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FixedWindowCounterTest {

    private static final long T0 = 1_000_000L;

    @Test
    @DisplayName("한도까지는 허용하고 넘으면 거절한다")
    void allowsUpToTheLimit() {
        FixedWindowCounter counter = new FixedWindowCounter(100);
        for (int i = 0; i < 3; i++) {
            assertThat(counter.tryAcquire("a", 3, 3600, T0 + i)).as((i + 1) + "번째").isTrue();
        }
        assertThat(counter.tryAcquire("a", 3, 3600, T0 + 10)).as("4번째").isFalse();
        assertThat(counter.tryAcquire("a", 3, 3600, T0 + 11)).as("5번째").isFalse();
    }

    @Test
    @DisplayName("창이 끝나면 처음부터 센다 (정확히 창 길이만큼 지난 시각부터)")
    void windowResets() {
        FixedWindowCounter counter = new FixedWindowCounter(100);
        assertThat(counter.tryAcquire("a", 1, 60, T0)).isTrue();
        assertThat(counter.tryAcquire("a", 1, 60, T0 + 59)).as("59초 뒤").isFalse();
        assertThat(counter.tryAcquire("a", 1, 60, T0 + 60)).as("60초 뒤").isTrue();
        assertThat(counter.tryAcquire("a", 1, 60, T0 + 61)).as("새 창에서 다시 한도").isFalse();
    }

    @Test
    @DisplayName("키마다 따로 센다")
    void keysAreIndependent() {
        FixedWindowCounter counter = new FixedWindowCounter(100);
        assertThat(counter.tryAcquire("a", 1, 3600, T0)).isTrue();
        assertThat(counter.tryAcquire("a", 1, 3600, T0)).isFalse();
        assertThat(counter.tryAcquire("b", 1, 3600, T0)).isTrue();
    }

    @Test
    @DisplayName("한도를 넘긴 시도는 횟수를 올리지 않는다 (창이 끝난 뒤 바로 다시 쓸 수 있다)")
    void rejectedAttemptsDoNotCount() {
        FixedWindowCounter counter = new FixedWindowCounter(100);
        assertThat(counter.tryAcquire("a", 2, 10, T0)).isTrue();
        assertThat(counter.tryAcquire("a", 2, 10, T0 + 1)).isTrue();
        for (int i = 0; i < 50; i++) {
            counter.tryAcquire("a", 2, 10, T0 + 2);
        }
        assertThat(counter.tryAcquire("a", 2, 10, T0 + 10)).as("창이 끝나면 새로 시작").isTrue();
        assertThat(counter.tryAcquire("a", 2, 10, T0 + 11)).isTrue();
        assertThat(counter.tryAcquire("a", 2, 10, T0 + 12)).isFalse();
    }

    @Test
    @DisplayName("끝난 창은 치워서 키가 쌓이지 않는다")
    void pruneRemovesExpiredWindows() {
        FixedWindowCounter counter = new FixedWindowCounter(100);
        for (int i = 0; i < 10; i++) {
            counter.tryAcquire("k" + i, 5, 60, T0);
        }
        assertThat(counter.size()).isEqualTo(10);
        counter.prune(T0 + 59);
        assertThat(counter.size()).as("아직 안 끝남").isEqualTo(10);
        counter.prune(T0 + 60);
        assertThat(counter.size()).as("끝남").isEqualTo(0);
    }

    @Test
    @DisplayName("키 수가 상한에 이르면 끝난 창을 먼저 치우고, 그래도 가득 차 있으면 새 키를 거절한다")
    void capsTheNumberOfKeys() {
        FixedWindowCounter counter = new FixedWindowCounter(3);
        assertThat(counter.tryAcquire("a", 5, 3600, T0)).isTrue();
        assertThat(counter.tryAcquire("b", 5, 3600, T0)).isTrue();
        assertThat(counter.tryAcquire("c", 5, 3600, T0)).isTrue();
        assertThat(counter.tryAcquire("d", 5, 3600, T0 + 1)).as("가득 참: 새 키 거절").isFalse();
        assertThat(counter.tryAcquire("a", 5, 3600, T0 + 1)).as("이미 있는 키는 계속 센다").isTrue();
        assertThat(counter.tryAcquire("d", 5, 3600, T0 + 3600)).as("창이 끝나면 자리가 난다").isTrue();
    }

    @Test
    @DisplayName("한도와 창 길이는 1 이상이어야 한다")
    void rejectsInvalidSettings() {
        FixedWindowCounter counter = new FixedWindowCounter(10);
        boolean limitRejected = false;
        boolean windowRejected = false;
        try {
            counter.tryAcquire("a", 0, 60, T0);
        } catch (IllegalArgumentException e) {
            limitRejected = true;
        }
        try {
            counter.tryAcquire("a", 1, 0, T0);
        } catch (IllegalArgumentException e) {
            windowRejected = true;
        }
        assertThat(limitRejected).isTrue();
        assertThat(windowRejected).isTrue();
    }
}