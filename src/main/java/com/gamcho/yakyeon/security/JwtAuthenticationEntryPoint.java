package com.gamcho.yakyeon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * httpBasic/formLogin을 둘 다 꺼둬서 Spring Security에 기본 AuthenticationEntryPoint가
 * 없는 상태였다 - 그래서 인증 안 된 요청이 403(바디 없음)으로 나가고 있었다.
 * REST API 명세의 공통 에러 포맷({ code, message })과 401을 맞추기 위해 직접 구현한다.
 *
 * ObjectMapper로 직렬화하지 않고 문자열을 직접 쓰는 이유: 이 프로젝트의 Jackson 버전이
 * (Spring Boot 4.x 기준) 신버전 패키지(tools.jackson.*)를 쓰는지 구버전(com.fasterxml.jackson.*)을
 * 쓰는지 빌드 설정에 따라 달라질 수 있어서, 고정된 두 필드짜리 응답은 그냥 문자열로
 * 처리하는 게 더 안전하다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":\"UNAUTHENTICATED\",\"message\":\"인증이 필요합니다. 로그인 후 다시 시도해주세요.\"}"
        );
    }
}