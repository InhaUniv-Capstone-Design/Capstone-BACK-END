package com.gamcho.yakyeon.domain.notification.push;

import com.gamcho.yakyeon.domain.notification.logic.MockPushRules;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 개발용 가짜 푸시 발송자. 실제로 아무데도 보내지 않고, 토큰 이름으로 결과를 흉내 낸다 (MockPushRules).
 * 설정 push.provider: mock 이 명시돼야만 켜진다 - 값이 없으면 앱이 시작되지 않아서, 실수로 설정 없이
 * 배포되는 일을 막는다 (OCR과 같은 방식). 토큰이나 알림 내용은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "push.provider", havingValue = "mock")
public class MockPushSender implements PushSender {

    @Override
    public PushResult send(PushMessage message) {
        return switch (MockPushRules.classify(message.fcmToken())) {
            case SENT -> PushResult.sent("mock-" + UUID.randomUUID());
            case TRANSIENT_FAILURE -> PushResult.transientFailure("TRANSIENT_FAILURE");
            case INVALID_TOKEN -> PushResult.invalidToken();
        };
    }
}