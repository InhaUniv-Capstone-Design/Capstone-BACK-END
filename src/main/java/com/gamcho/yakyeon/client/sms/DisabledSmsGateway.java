package com.gamcho.yakyeon.client.sms;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 문자 발송을 쓰지 않는 서버용 (sms.provider=disabled). 개발용 MockSmsGateway는 인증번호를 로그에 평문으로 남기므로
 * 실제 서버에서는 로드하지 않아야 하는데, 옛 방식(보호자 연동 문자 인증)이 이 인터페이스를 주입받아서 구현체가 하나는 있어야 한다.
 * 옛 방식은 기본 꺼짐(features.legacy-sms-consent)이라 이 구현체가 불릴 일은 없고, 실수로 켜져 있으면 문자를 보내지 않고 거절한다.
 * 가입 문자 인증은 OCTOMO(phone-verification.provider=octomo)를 쓰므로 이 인터페이스를 쓰지 않는다.
 */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "disabled")
public class DisabledSmsGateway implements SmsGateway {

    @Override
    public void send(String phone, String message) {
        throw new BusinessException(ErrorCode.FEATURE_DISABLED);
    }
}