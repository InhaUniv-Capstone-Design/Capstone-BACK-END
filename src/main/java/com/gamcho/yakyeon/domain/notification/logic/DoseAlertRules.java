package com.gamcho.yakyeon.domain.notification.logic;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 복약자 본인에게 가는 알림의 시점 (스프링·DB 없는 순수 로직).
 *
 * 앱 "알림 설정" 화면의 문구와 같다.
 *  - 복용 시간 알림: 예정 10분 전에 미리 알린다. 정각에는 약통(LED·부저)이 울리므로 앱은 정각에 알리지 않는다.
 *  - 미복용 알림:    예정 30분이 지났는데도 기록이 없으면 "다시" 알린다. 보호자에게는 1시간 뒤에 따로 간다(MissedDoseRules).
 * 마감(예정 3시간 뒤)이 지난 예정은 누락으로 확정되는 중이므로 미복용 알림 대상이 아니다.
 */
public final class DoseAlertRules {

    /** 예정 시각의 이만큼 전부터 복용 시간 알림 대상이 된다 */
    public static final Duration REMINDER_BEFORE = Duration.ofMinutes(10);

    /** 예정 시각에서 이만큼 지나도 복용이 없으면 본인에게 미복용 알림을 보낸다 */
    public static final Duration SELF_MISSED_AFTER = Duration.ofMinutes(30);

    private DoseAlertRules() {
    }

    /** 이 시각 이전(포함)에 시작하는 예정이 복용 시간 알림 대상이다 (지금 이후 시작분만) */
    public static OffsetDateTime reminderUntil(OffsetDateTime now) {
        return now.plus(REMINDER_BEFORE);
    }

    /** 이 시각 이전(포함)에 시작한 예정이 본인 미복용 알림 대상이다 */
    public static OffsetDateTime selfMissedCutoff(OffsetDateTime now) {
        return now.minus(SELF_MISSED_AFTER);
    }

    /** 복용 시간 알림을 보낼 때가 된 예정인지: 예정 10분 전부터 예정 시각 직전까지 (정각부터는 약통 담당) */
    public static boolean isReminderDue(OffsetDateTime scheduledAt, OffsetDateTime now) {
        return scheduledAt.isAfter(now) && !scheduledAt.isAfter(reminderUntil(now));
    }

    /** 본인 미복용 알림을 보낼 때가 된 예정인지: 예정 30분이 지났고(정각 포함) 아직 마감 전 */
    public static boolean isSelfMissedDue(OffsetDateTime scheduledAt, OffsetDateTime closeAt, OffsetDateTime now) {
        return !scheduledAt.plus(SELF_MISSED_AFTER).isAfter(now) && closeAt.isAfter(now);
    }
}