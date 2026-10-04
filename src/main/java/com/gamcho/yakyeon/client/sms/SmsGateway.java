package com.gamcho.yakyeon.client.sms;

/**
 * SMS 발송 추상화. 지금은 MockSmsGateway만 있고, 실제 SMS 업체 연동 시
 * 이 인터페이스의 구현체를 추가하면 서비스 코드는 수정할 필요가 없다.
 */
public interface SmsGateway {

    /**
     * @param phone   숫자만 있는 수신 번호 (예: 01012345678)
     * @param message 발송할 본문
     */
    void send(String phone, String message);
}