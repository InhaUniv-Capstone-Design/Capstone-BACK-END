package com.gamcho.yakyeon.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * API 응답에 실리는 시각의 변환 규칙 - 응답의 시각은 항상 UTC 순간(Instant, "...Z")으로 내보낸다.
 * (REST API 명세 §0: 시각은 ISO-8601 UTC 문자열로 송수신하고, 화면 표시용 Asia/Seoul 변환은 클라이언트가 한다)
 *
 * 엔티티의 LocalDateTime은 DB(timestamptz)와 JVM 기본 시간대로 변환되어 오가므로,
 * 응답으로 내보낼 때도 같은 시간대로 "어느 순간인지"를 복원한 뒤 UTC로 내보낸다.
 * 이렇게 하면 서버를 다른 시간대의 머신으로 옮겨도 같은 행은 같은 순간으로 나간다.
 *
 * 시각이 들어가는 모든 응답 DTO는 LocalDateTime을 그대로 담지 말고 이 메서드를 거칠 것.
 */
public final class ApiTime {

    private ApiTime() {
    }

    public static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** 서비스 기준 시간대. 복약 일정의 "오늘"은 사용자의 하루(한국)를 기준으로 한다 */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 서비스 기준(한국)의 오늘 날짜.
     * LocalDate.now()는 서버 JVM의 시간대를 따라서, 서버를 UTC 머신으로 옮기면 한국 시각 00~09시 사이에
     * 날짜가 하루 어긋난다. 복약 서비스는 날짜 경계(복용 시작·종료, 오늘 예정)가 핵심이라 시간대를 고정한다.
     * 날짜로 "오늘"을 판단하는 모든 곳은 LocalDate.now() 대신 이걸 쓸 것.
     */
    public static LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }
}