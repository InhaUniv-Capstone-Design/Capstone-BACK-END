package com.gamcho.yakyeon.domain.notification.logic;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 푸시 발송의 규칙 (스프링·DB 없는 순수 로직).
 *
 *  - 일시 실패는 간격을 늘려 가며 다시 보낸다: 1분, 5분, 15분, 60분 뒤. 다섯 번째 시도까지 실패하면 포기한다.
 *  - 너무 오래된 알림은 푸시로 보내지 않는다 (24시간). 몇 시간 뒤에 "약통이 열렸어요"가 오면 오히려 혼란스럽다.
 *    알림 목록에는 남아 있어서 앱을 열면 볼 수 있다.
 *  - 이미 읽은 알림, 알림 권한을 끈 기기, 지금은 볼 수 없는 복약자(연동 해제)의 알림은 보내지 않는다.
 */
public final class PushDeliveryRules {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration MAX_PUSH_AGE = Duration.ofHours(24);
    /** n번째 시도가 실패했을 때 다음 시도까지의 간격 (인덱스 0 = 1번째 실패) */
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60));

    private PushDeliveryRules() {
    }

    /** @param attemptNo 방금 실패한 시도 번호 (1부터). 더 시도할 수 없으면 비어 있다 */
    public static Optional<OffsetDateTime> nextRetryAt(int attemptNo, OffsetDateTime now) {
        if (attemptNo < 1 || attemptNo >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        return Optional.of(now.plus(BACKOFF.get(attemptNo - 1)));
    }

    /** 생성된 지 24시간이 지났으면(정각 포함) 푸시로 보내지 않는다 */
    public static boolean isStale(OffsetDateTime createdAt, OffsetDateTime now) {
        return !createdAt.plus(MAX_PUSH_AGE).isAfter(now);
    }

    /** 지금 이 알림을 이 기기로 보내도 되는지 */
    public static boolean isPushable(boolean unread, boolean tokenPermitted, boolean recipientCanStillSee,
                                     OffsetDateTime createdAt, OffsetDateTime now) {
        return unread && tokenPermitted && recipientCanStillSee && !isStale(createdAt, now);
    }
}