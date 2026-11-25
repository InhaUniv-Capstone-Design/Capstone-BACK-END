package com.gamcho.yakyeon.domain.notification.logic;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 미복용 알림의 시점 (스프링·DB 없는 순수 로직).
 *
 * 누락 확정(예정 3시간 뒤)과는 별개로, 예정 시각에서 이 시간이 지났는데도 복용이 기록되지 않았으면
 * 보호자에게 알려 준다. 이미 마감이 지난 예정은 누락으로 확정되는 중이므로 대상이 아니다.
 */
public final class MissedDoseRules {

    /** 예정 시각에서 이만큼 지나도 복용이 없으면 알린다 */
    public static final Duration ALERT_AFTER = Duration.ofHours(1);

    private MissedDoseRules() {
    }

    /** 이 시각 이전(포함)에 시작한 예정이 알림 대상이다 */
    public static OffsetDateTime cutoff(OffsetDateTime now) {
        return now.minus(ALERT_AFTER);
    }

    /** 알림을 보낼 때가 된 예정인지: 예정 시각에서 1시간이 지났고(정각 포함), 아직 마감 전 */
    public static boolean isDue(OffsetDateTime scheduledAt, OffsetDateTime closeAt, OffsetDateTime now) {
        return !scheduledAt.plus(ALERT_AFTER).isAfter(now) && closeAt.isAfter(now);
    }
}