package com.gamcho.yakyeon.domain.account.logic;

/**
 * 로그인 연속 실패 잠금 설정을 안전한 범위로 고친다 (스프링·DB 없는 순수 로직).
 * 설정 실수로 한 번만 틀려도 잠기거나(1), 사실상 잠기지 않거나(너무 큰 값), 영원히 잠기는 일이 없게 범위를 둔다.
 */
public final class LoginLockRules {

    public static final int MIN_FAILURES = 2;
    public static final int MAX_FAILURES = 20;
    public static final int MIN_LOCK_MINUTES = 1;
    public static final int MAX_LOCK_MINUTES = 24 * 60;

    private LoginLockRules() {
    }

    /** 이 횟수만큼 연속으로 틀리면 잠근다 (2~20) */
    public static int maxFailures(int configured) {
        return Math.max(MIN_FAILURES, Math.min(MAX_FAILURES, configured));
    }

    /** 잠금 시간(분, 1~1440) */
    public static int lockMinutes(int configured) {
        return Math.max(MIN_LOCK_MINUTES, Math.min(MAX_LOCK_MINUTES, configured));
    }
}