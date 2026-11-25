package com.gamcho.yakyeon.domain.account.consent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 서비스 필수 동의(이용약관, 개인정보, 건강정보)를 철회한 사용자도 쓸 수 있는 API 표시.
 *
 * 필수 동의를 철회하면 건강정보 API는 모두 403 CONSENT_REQUIRED가 되고(RequiredConsentInterceptor),
 * 이 표시가 붙은 API만 예외다: 약관 동의 관리, 프로필 조회, 계정 삭제, 토큰 갱신·로그아웃.
 * 새 API는 기본이 "동의 필요"이고, 예외로 열려면 여기에 명시적으로 표시해야 한다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ConsentExempt {
}