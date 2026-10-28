package com.gamcho.yakyeon.domain.notification.push;

/**
 * 발송 결과.
 *  SENT              - 보냈다 (messageId가 있다)
 *  TRANSIENT_FAILURE - 일시 실패. 나중에 다시 보낸다
 *  INVALID_TOKEN     - 토큰이 무효하다(앱 삭제·토큰 만료). 다시 보내도 소용없어서 토큰을 지운다
 */
public record PushResult(Status status, String messageId, String errorCode) {

    public enum Status {
        SENT, TRANSIENT_FAILURE, INVALID_TOKEN
    }

    public static PushResult sent(String messageId) {
        return new PushResult(Status.SENT, messageId, null);
    }

    public static PushResult transientFailure(String errorCode) {
        return new PushResult(Status.TRANSIENT_FAILURE, null, errorCode);
    }

    public static PushResult invalidToken() {
        return new PushResult(Status.INVALID_TOKEN, null, "INVALID_TOKEN");
    }
}