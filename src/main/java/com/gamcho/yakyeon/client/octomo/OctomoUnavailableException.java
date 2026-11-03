package com.gamcho.yakyeon.client.octomo;

/**
 * OCTOMO를 쓸 수 없을 때(연결 실패, 시간 초과, 오류 응답, 호출 한도). 메시지에는 원인 종류와 상태 코드만 담고
 * 요청·응답 내용과 API Key는 절대 담지 않는다(로그에 남아도 안전해야 한다).
 */
public class OctomoUnavailableException extends RuntimeException {

    public OctomoUnavailableException(String reason) {
        super(reason);
    }
}