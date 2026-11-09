package com.gamcho.yakyeon.domain.notification.push.fcm;

/** 구글 인증(액세스 토큰 받기)에 실패했다. reason은 원인만 담는다(키·토큰·응답 본문은 담지 않는다) */
public class FcmAuthException extends RuntimeException {

    private final String reason;

    public FcmAuthException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}