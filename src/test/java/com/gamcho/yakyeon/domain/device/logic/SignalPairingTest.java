package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignalPairingTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-11-01T" + time + "+09:00");
    }

    private static SignalPairing.Sig ir(long id, String time) {
        return new SignalPairing.Sig(id, at(time));
    }

    @Test
    @DisplayName("버튼 앞 60초 안의 열림은 같은 복용으로 짝이 된다")
    void irWithinWindowPairs() {
        Optional<SignalPairing.IrMatch> match =
                SignalPairing.matchIrForButton(at("08:00:30"), List.of(ir(1, "08:00:10")), WINDOW);
        assertThat(match.isPresent()).isTrue();
        assertThat(match.get().primary().signalId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("허용 시간 경계(정확히 60초 전)는 짝이 되고, 1초라도 더 오래되면 짝이 아니다")
    void windowBoundary() {
        assertThat(SignalPairing.matchIrForButton(at("08:01:00"), List.of(ir(1, "08:00:00")), WINDOW).isPresent()).isTrue();
        assertThat(SignalPairing.matchIrForButton(at("08:01:01"), List.of(ir(1, "08:00:00")), WINDOW).isPresent()).isFalse();
    }

    @Test
    @DisplayName("버튼보다 나중에 온 열림은 짝이 아니다 (순서는 도착 순서가 아니라 발생 시각이다)")
    void irAfterButtonIsNotPaired() {
        assertThat(SignalPairing.matchIrForButton(at("08:00:30"), List.of(ir(1, "08:00:31")), WINDOW).isPresent()).isFalse();
    }

    @Test
    @DisplayName("열림이 없으면 짝이 없다 (버튼만 눌린 경우)")
    void noIrMeansNoMatch() {
        assertThat(SignalPairing.matchIrForButton(at("08:00:30"), List.of(), WINDOW).isPresent()).isFalse();
    }

    @Test
    @DisplayName("손을 여러 번 넣었다 빼서 열림이 여럿이면 가장 먼저 열린 것이 열린 시각이고, 모두 같은 복용으로 소비한다")
    void multipleIrAreConsumedTogether() {
        SignalPairing.IrMatch match = SignalPairing.matchIrForButton(at("08:00:50"),
                List.of(ir(3, "08:00:30"), ir(1, "08:00:10"), ir(2, "08:00:10")), WINDOW).get();
        assertThat(match.primary().signalId()).isEqualTo(1L);   // 같은 시각이면 신호 번호가 작은 쪽
        assertThat(match.all().size()).isEqualTo(3);
        assertThat(match.all().get(0).signalId()).isEqualTo(1L);
        assertThat(match.all().get(2).signalId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("복용 직후 허용 시간 안의 반복(두 번째 버튼, 손 빼며 생긴 열림)은 같은 복용으로 본다")
    void repeatWithinWindowAfterConfirmed() {
        OffsetDateTime confirmed = at("08:00:00");
        assertThat(SignalPairing.isWithinWindowAfter(null, at("08:00:10"), WINDOW)).as("확인된 복용 없음").isFalse();
        assertThat(SignalPairing.isWithinWindowAfter(confirmed, at("08:00:00"), WINDOW)).as("같은 시각").isTrue();
        assertThat(SignalPairing.isWithinWindowAfter(confirmed, at("08:01:00"), WINDOW)).as("정확히 60초 뒤").isTrue();
        assertThat(SignalPairing.isWithinWindowAfter(confirmed, at("08:01:01"), WINDOW)).as("61초 뒤").isFalse();
        assertThat(SignalPairing.isWithinWindowAfter(confirmed, at("07:59:59"), WINDOW)).as("복용보다 앞").isFalse();
    }

    @Test
    @DisplayName("약통 시계가 서버보다 5분까지 앞서는 것은 오차로 보고, 더 앞서면 서버 시각으로 바꾼다")
    void clockSkew() {
        OffsetDateTime now = at("08:00:00");
        assertThat(SignalPairing.clampFuture(at("08:05:00"), now)).as("정확히 5분 앞").isEqualTo(at("08:05:00"));
        assertThat(SignalPairing.clampFuture(at("08:05:01"), now)).as("5분 1초 앞").isEqualTo(now);
        assertThat(SignalPairing.clampFuture(at("07:00:00"), now)).as("과거(오프라인 중 눌림)").isEqualTo(at("07:00:00"));
    }

    @Test
    @DisplayName("열림만 오고 버튼이 안 왔다는 확정은 서버가 받은 시각 기준으로 허용 시간이 지난 뒤(정각 포함)에만 한다")
    void staleIsBasedOnReceivedTime() {
        OffsetDateTime received = at("08:00:00");
        assertThat(SignalPairing.isStale(received, WINDOW, at("08:00:59"))).isFalse();
        assertThat(SignalPairing.isStale(received, WINDOW, at("08:01:00"))).isTrue();
        assertThat(SignalPairing.isStale(received, WINDOW, at("08:01:01"))).isTrue();
    }
}