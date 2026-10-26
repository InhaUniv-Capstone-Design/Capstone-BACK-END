package com.gamcho.yakyeon.domain.dose.logic;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 앱에서 복약을 기록·정정할 때의 판단 규칙 (DB·스프링 없는 순수 로직).
 * DB의 정정 함수는 정상/지연을 판단하지 않고 받은 값을 그대로 저장하므로, 복용 시각으로 상태를 정하는 일은 서버가 한다.
 */
public final class DoseRecordRules {

    /** 예정 시각보다 이 시간 이전의 복용 시각은 기록할 수 없다 (임시 기준 - 팀에서 정하면 이 상수만 바꾼다) */
    public static final Duration EARLIEST_BEFORE_SCHEDULED = Duration.ofHours(6);

    /** 이 시간 안에서 같은 상태로 다시 기록하면 같은 기록으로 본다 (같은 버튼을 두 번 누른 경우) */
    public static final Duration SAME_RECORD_TOLERANCE = Duration.ofSeconds(60);

    private DoseRecordRules() {
    }

    /** 정상 인정 시각까지(그 시각 포함)면 정상, 이후는 지연. 예정보다 일찍 먹은 것은 정상이다. */
    public static String statusFor(OffsetDateTime takenAt, OffsetDateTime normalUntil) {
        return takenAt.isAfter(normalUntil) ? "DELAYED" : "NORMAL";
    }

    /** 복용 시각이 받아들일 수 있는지. 문제가 있으면 사용자에게 보여줄 문장을 돌려준다 */
    public static Optional<String> takenAtViolation(OffsetDateTime takenAt, OffsetDateTime scheduledAt,
                                                    OffsetDateTime now) {
        if (takenAt.isAfter(now)) {
            return Optional.of("복용 시각이 현재보다 미래입니다.");
        }
        if (takenAt.isBefore(scheduledAt.minus(EARLIEST_BEFORE_SCHEDULED))) {
            return Optional.of("복용 시각이 예정 시각보다 너무 이릅니다. (예정 "
                    + EARLIEST_BEFORE_SCHEDULED.toHours() + "시간 전부터 기록할 수 있습니다)");
        }
        return Optional.empty();
    }

    /** 예정과 지금이 같은 한국 날짜인지. DB 정정 함수는 당일 기록만 정정할 수 있다 */
    public static boolean sameDay(OffsetDateTime scheduledAt, OffsetDateTime now, ZoneId zone) {
        return scheduledAt.atZoneSameInstant(zone).toLocalDate().equals(now.atZoneSameInstant(zone).toLocalDate());
    }

    /**
     * 이미 같은 기록이 있는지 (같은 상태이고 복용 시각이 거의 같다). 같으면 정정 이력을 또 남기지 않고 그대로 돌려준다.
     */
    public static boolean isSameRecord(String currentStatus, OffsetDateTime currentTakenAt,
                                       String newStatus, OffsetDateTime newTakenAt) {
        if (!currentStatus.equals(newStatus) || currentTakenAt == null || newTakenAt == null) {
            return false;
        }
        return Duration.between(currentTakenAt, newTakenAt).abs().compareTo(SAME_RECORD_TOLERANCE) < 0;
    }

    /** "안 먹었어요"의 결과: 마감 전이면 다시 예정(PENDING)으로, 마감이 지났으면 누락(MISSED) (DB 정정 함수와 같은 기준) */
    public static String notTakenResult(OffsetDateTime closeAt, OffsetDateTime now) {
        return now.isBefore(closeAt) ? "PENDING" : "MISSED";
    }
}