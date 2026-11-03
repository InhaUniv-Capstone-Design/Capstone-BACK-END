package com.gamcho.yakyeon.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 같은 접속 주소가 이 API를 부를 수 있는 횟수를 제한한다: windowSeconds 동안 limit번까지, 넘으면 429 RATE_LIMITED.
 * 로그인 전에 열려 있는 공개 API(가입 문자 인증 등)를 대량으로 부르는 것을 막으려고 쓴다.
 * name이 같은 API끼리 한도를 나눠 쓴다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    String name();

    int limit();

    int windowSeconds();
}