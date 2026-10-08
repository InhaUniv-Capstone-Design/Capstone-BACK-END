package com.gamcho.yakyeon.common.util;

import java.time.Instant;
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
}