package com.gamcho.yakyeon.common.feature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 옛 문자 인증 방식(보호자가 인증번호를 입력해 위임 동의를 받는 흐름, 문자 인증 기반 철회 등)의 엔드포인트 표시.
 *
 * 정식 흐름은 복약자 웹 QR 동의라서 이 방식은 디자인에서 쓰지 않는다. 코드는 지우지 않고 남겨 두되,
 * 설정 features.legacy-sms-consent=true 일 때만 동작하게 한다(기본은 꺼짐).
 * 꺼져 있으면 컨트롤러에 닿기 전에 404 FEATURE_DISABLED로 돌려보낸다(LegacyConsentInterceptor).
 *
 * 메서드나 컨트롤러 클래스에 붙인다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LegacyConsentOnly {
}