package com.gamcho.yakyeon.domain.device.logic;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 약통 신호(칸 열림 IR, 복용 확인 버튼)를 한 번의 복용으로 묶는 규칙 (스프링·DB 없는 순수 로직).
 *
 * 열림이 먼저 오고 같은 칸의 버튼이 허용 시간(pair_window_sec, 기본 60초) 안에 오면 한 번의 복용이다.
 * 허용 시간은 열린 시각부터 버튼 시각까지, 양 끝 포함으로 센다. 기기 시각이 서로 다른 신호의 앞뒤를 정하므로
 * 서버가 받은 순서가 아니라 신호에 찍힌 발생 시각으로 비교한다.
 */
public final class SignalPairing {

    /** 기기 시계가 서버보다 이만큼까지 앞서는 것은 오차로 보고 그대로 쓴다. 더 앞서면 서버가 받은 시각으로 바꾼다 */
    public static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

    private SignalPairing() {
    }

    /** 아직 처리되지 않은 열림 신호 한 건 */
    public record Sig(long signalId, OffsetDateTime occurredAt) {
    }

    /**
     * @param primary 이 복용의 "열린 시각"이 되는 신호(가장 먼저 열린 것)
     * @param all     허용 시간 안의 모든 열림 신호. 손을 넣었다 뺐다 하면 열림이 여러 번 올 수 있어서 모두 같은 복용으로 소비한다
     */
    public record IrMatch(Sig primary, List<Sig> all) {
    }

    /** 버튼 시각 기준으로 허용 시간 안에 있는 열림 신호를 찾는다. 없으면 버튼만 눌린 것(BUTTON_ONLY)이다 */
    public static Optional<IrMatch> matchIrForButton(OffsetDateTime buttonAt, List<Sig> unprocessedIr, Duration window) {
        OffsetDateTime from = buttonAt.minus(window);
        List<Sig> inWindow = unprocessedIr.stream()
                .filter(s -> !s.occurredAt().isBefore(from) && !s.occurredAt().isAfter(buttonAt))
                .sorted(Comparator.comparing(Sig::occurredAt).thenComparingLong(Sig::signalId))
                .toList();
        if (inWindow.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new IrMatch(inWindow.get(0), inWindow));
    }

    /**
     * 직전에 확인된 복용 시각 뒤 허용 시간 안인지. 같은 복용의 반복으로 보는 데 쓴다:
     * 버튼을 연달아 두 번 누른 경우(둘째는 무시), 복용 직후 손을 빼다가 열림 신호가 또 온 경우(무시).
     */
    public static boolean isWithinWindowAfter(OffsetDateTime lastConfirmedAt, OffsetDateTime at, Duration window) {
        return lastConfirmedAt != null && !at.isBefore(lastConfirmedAt) && !at.isAfter(lastConfirmedAt.plus(window));
    }

    /** 기기 시계가 서버보다 많이 앞서 있으면(미래 시각) 서버가 받은 시각으로 바꾼다 */
    public static OffsetDateTime clampFuture(OffsetDateTime occurredAt, OffsetDateTime now) {
        return occurredAt.isAfter(now.plus(CLOCK_TOLERANCE)) ? now : occurredAt;
    }

    /**
     * 열림 신호를 "버튼이 안 왔다"고 확정해도 되는지. 기기 시계가 아니라 서버가 받은 시각 기준으로
     * 허용 시간이 지났을 때(정각 포함)만 확정한다 - 기기 시계가 틀려도 버튼이 올 시간을 충분히 기다린다.
     */
    public static boolean isStale(OffsetDateTime receivedAt, Duration window, OffsetDateTime now) {
        return !receivedAt.plus(window).isAfter(now);
    }
}