package com.gamcho.yakyeon.client.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제로 문자를 보내지 않고 서버 로그에만 남기는 개발용 구현체.
 * 개발 중에는 이 로그에서 인증번호를 확인한다.
 *
 * ⚠️ 이 구현체는 인증번호를 로그에 평문으로 남긴다. 실제 SMS 업체를 연동하면
 * 반드시 sms.provider를 다른 값으로 바꿔서 이 빈이 로드되지 않게 해야 한다
 * (지금은 실제 구현체가 없어서 설정이 없으면 이 Mock이 기본값으로 동작한다).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsGateway implements SmsGateway {

    @Override
    public void send(String phone, String message) {
        String tail = phone.substring(Math.max(0, phone.length() - 4));
        log.info("[MOCK SMS] to=***{} message={}", tail, message);
    }
}