package com.gamcho.yakyeon.common.feature;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 옛 문자 인증 방식을 켜고 끄는 스위치. 설정 features.legacy-sms-consent (기본 false).
 * 켜려면 application-local.yml 등에 다음을 추가한다.
 *
 *   features:
 *     legacy-sms-consent: true
 */
@Component
public class LegacyConsentFeature {

    private final boolean enabled;

    public LegacyConsentFeature(@Value("${features.legacy-sms-consent:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 꺼져 있으면 404 FEATURE_DISABLED. 요청 단위로 막을 수 없는 곳(서비스 안의 분기)에서 쓴다. */
    public void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(ErrorCode.FEATURE_DISABLED);
        }
    }
}