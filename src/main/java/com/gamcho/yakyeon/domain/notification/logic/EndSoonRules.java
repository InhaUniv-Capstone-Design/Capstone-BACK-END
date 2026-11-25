package com.gamcho.yakyeon.domain.notification.logic;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 복용 종료 예정 알림의 시점 (스프링·DB 없는 순수 로직).
 *
 * 앱 "알림 설정" 화면의 문구("종료 3일 전에 알려드려요")와 같다. 처방(약봉지) 안의 약 중 가장 늦게 끝나는 약의
 * 마지막 복용일이 오늘부터 3일 뒤 사이에 들어오면, 그날 오전 9시 이후 처음 도는 점검에서 한 번 알린다.
 * 같은 처방에 같은 사람에게는 DB의 유일 인덱스가 한 번만 허용한다.
 */
public final class EndSoonRules {

    /** 마지막 복용일의 이만큼 전부터 알린다 */
    public static final int NOTICE_DAYS = 3;

    /** 알림은 이 시각 이후에 보낸다 (한밤중에 푸시가 울리지 않게) */
    public static final LocalTime NOTICE_FROM = LocalTime.of(9, 0);

    private EndSoonRules() {
    }

    /** 오늘부터 이 날짜까지 끝나는 처방이 알림 대상이다 */
    public static LocalDate noticeUntil(LocalDate today) {
        return today.plusDays(NOTICE_DAYS);
    }

    /** 지금이 알림을 보내도 되는 시각인지 (오전 9시 이후) */
    public static boolean isNoticeTime(LocalTime now) {
        return !now.isBefore(NOTICE_FROM);
    }

    /**
     * 이 처방이 알림 대상인지.
     *  - 가장 늦은 마지막 복용일이 오늘 ~ 3일 뒤 사이
     *  - 가장 이른 시작일이 오늘보다 앞: 오늘 막 시작한 짧은 처방(예: 3일치)에 "곧 끝나요"를 보내지 않는다
     */
    public static boolean isDue(LocalDate firstStart, LocalDate lastEnd, LocalDate today) {
        return !lastEnd.isBefore(today)
                && !lastEnd.isAfter(noticeUntil(today))
                && firstStart.isBefore(today);
    }
}