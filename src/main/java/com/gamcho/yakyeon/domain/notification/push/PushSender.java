package com.gamcho.yakyeon.domain.notification.push;

/**
 * 푸시를 보내는 자리. 지금은 MockPushSender만 있고, 실제 FCM으로 바꿀 때는 이 인터페이스를 구현하는
 * FcmPushSender를 만들어 설정값(push.provider)만 바꾸면 된다. 알림 생성, 발송 기록, 재시도 같은 나머지는 그대로다.
 *
 * 구현체는 네트워크 호출이 있을 수 있어서 DB 트랜잭션 밖에서 불린다 (PushDispatchService 참고).
 * 예외를 던지지 말고 결과(PushResult)로 돌려줘야 한다.
 */
public interface PushSender {

    PushResult send(PushMessage message);

    /**
     * 알림 창 없이 앱에게 "다시 조회하라"고만 알리는 데이터 푸시 (DataPush). 알림 문구도 알림 번호도 없고, 실패해도 다시 보내지 않는다.
     * 약속은 send와 같다: 네트워크 호출이 있을 수 있어 트랜잭션 밖에서 부르고, 예외를 던지지 말고 결과로 돌려준다.
     */
    PushResult sendData(DataPush push);
}