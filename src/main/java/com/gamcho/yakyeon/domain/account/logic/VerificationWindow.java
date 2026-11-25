package com.gamcho.yakyeon.domain.account.logic;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * OCTOMO에 "최근 몇 분 안에 온 문자인가"를 물을 때 쓰는 조회 범위 (스프링 없는 순수 로직).
 *
 * 인증을 요청한 시각부터 지금까지 경과한 시간을 분으로 올림해서, 요청 이전에 온 문자는 범위에 들어오지 않게 한다.
 * OCTOMO가 분 단위(1~60)만 받아서 올림하면 요청 직전 최대 59초가 겹칠 수 있지만, 인증번호는 요청할 때 새로 만든 값이라
 * 그 전에는 누구도 알 수 없으니 문제가 없다. 인증번호 유효시간이 5분이라 실제 값은 1~5다.
 */
public final class VerificationWindow {

    private VerificationWindow() {
    }

    public static int withinMinutes(LocalDateTime requestedAt, LocalDateTime now) {
        long seconds = Math.max(0, Duration.between(requestedAt, now).getSeconds());
        long minutes = (seconds + 59) / 60;
        return (int) Math.min(60, Math.max(1, minutes));
    }
}