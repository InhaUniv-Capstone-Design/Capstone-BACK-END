package com.gamcho.yakyeon.domain.share.logic;

import com.gamcho.yakyeon.domain.interaction.logic.MedicationRef;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 복약 이력 공유 요약의 계산 규칙 (스프링·DB 없는 순수 로직).
 *
 * 공유 기간은 양 끝 날짜를 포함해서 센다(기본 30일이면 오늘을 포함한 30일). 약의 "복용 중 / 복약한"은 공유 기간의
 * 마지막 날(to)을 기준으로 나눈다: 그날 복용 중이었으면 복용 중인 약물, 그 전에 끝났으면 복약한 약물이다.
 */
public final class ShareSummaryRules {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 90;

    private ShareSummaryRules() {
    }

    /** 공유 기간. 양 끝 날짜를 포함한다 */
    public record Period(LocalDate from, LocalDate to) {
        public int days() {
            return (int) ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    /** 날짜 범위 (양 끝 포함) */
    public record DateRange(LocalDate from, LocalDate to) {
    }

    public enum Kind {
        /** 공유 기간의 마지막 날에 복용 중인 약 */
        CURRENT,
        /** 공유 기간 안에 복용했고 마지막 날 전에 끝난 약 */
        PAST,
        /** 공유 기간과 상관없는 약 (기간 밖이거나, 시작하기도 전에 종료한 약) */
        NONE
    }

    /**
     * 공유 기간을 정한다. 둘 다 비어 있으면 오늘까지 최근 30일이다.
     *
     * @throws IllegalArgumentException 기간이 올바르지 않을 때 (메시지는 그대로 사용자에게 보여줄 수 있다)
     */
    public static Period resolvePeriod(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null && to == null) {
            return new Period(today.minusDays(DEFAULT_DAYS - 1L), today);
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from과 to를 함께 입력해주세요.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (to.isAfter(today)) {
            throw new IllegalArgumentException("종료일은 오늘 이후일 수 없습니다.");
        }
        Period period = new Period(from, to);
        if (period.days() > MAX_DAYS) {
            throw new IllegalArgumentException("공유 기간은 최대 " + MAX_DAYS + "일입니다.");
        }
        return period;
    }

    /**
     * 약을 실제로 복용한 마지막 날. 종료를 누른 날(endedOn)부터는 종료된 약이라 그 전날까지가 복용한 날이고,
     * 계획한 종료일(endDate)이 더 빠르면 그날이다. 종료를 누른 적이 없으면 계획한 종료일이다.
     */
    public static LocalDate lastActiveDate(LocalDate endDate, LocalDate endedOn) {
        if (endedOn == null) {
            return endDate;
        }
        LocalDate dayBeforeEnded = endedOn.minusDays(1);
        return dayBeforeEnded.isBefore(endDate) ? dayBeforeEnded : endDate;
    }

    /** 약을 공유 기간에 비춰 분류한다. lastActive는 lastActiveDate()로 구한 마지막 복용일이다 */
    public static Kind classify(LocalDate start, LocalDate lastActive, Period period) {
        if (lastActive.isBefore(start)) {
            return Kind.NONE;   // 복용을 시작하기 전에 종료했다
        }
        if (start.isAfter(period.to()) || lastActive.isBefore(period.from())) {
            return Kind.NONE;   // 공유 기간과 하루도 겹치지 않는다
        }
        return lastActive.isBefore(period.to()) ? Kind.PAST : Kind.CURRENT;
    }

    /**
     * 두 약을 함께 복용한 기간(각자 실제 복용한 날의 교집합)이 공유 기간과 하루라도 겹치면 그 기간을 돌려준다.
     * MedicationRef의 endDate에는 마지막 복용일(lastActiveDate)을 넣어서 부른다.
     */
    public static Optional<DateRange> togetherInPeriod(MedicationRef a, MedicationRef b, Period period) {
        LocalDate from = a.startDate().isAfter(b.startDate()) ? a.startDate() : b.startDate();
        LocalDate to = a.endDate().isBefore(b.endDate()) ? a.endDate() : b.endDate();
        if (from.isAfter(to)) {
            return Optional.empty();   // 같이 복용한 날이 없다
        }
        if (from.isAfter(period.to()) || to.isBefore(period.from())) {
            return Optional.empty();   // 같이 복용했지만 공유 기간 밖이다
        }
        return Optional.of(new DateRange(from, to));
    }
}