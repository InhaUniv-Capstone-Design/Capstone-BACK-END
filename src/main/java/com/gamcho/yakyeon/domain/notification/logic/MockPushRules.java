package com.gamcho.yakyeon.domain.notification.logic;

/**
 * 개발용 가짜 푸시 발송자가 토큰 이름으로 결과를 정하는 규칙. 실제 FCM의 실패를 흉내 내서
 * 재시도와 무효 토큰 처리를 기기 없이 시험하려는 용도다.
 *  - "bad-" 로 시작: 무효한 토큰 (앱 삭제·토큰 만료. 실제 FCM의 UNREGISTERED와 같다)
 *  - "fail-" 로 시작: 일시 실패 (네트워크·서버 문제. 실제 FCM의 UNAVAILABLE와 같다)
 *  - 그 밖: 성공
 */
public final class MockPushRules {

    public enum Outcome {
        SENT, TRANSIENT_FAILURE, INVALID_TOKEN
    }

    private MockPushRules() {
    }

    public static Outcome classify(String fcmToken) {
        if (fcmToken == null) {
            return Outcome.INVALID_TOKEN;
        }
        if (fcmToken.startsWith("bad-")) {
            return Outcome.INVALID_TOKEN;
        }
        if (fcmToken.startsWith("fail-")) {
            return Outcome.TRANSIENT_FAILURE;
        }
        return Outcome.SENT;
    }
}