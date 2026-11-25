package com.gamcho.yakyeon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 복약자 권한 관리 웹(브라우저)이 다른 주소에서 이 서버를 호출할 수 있게 허용하는 CORS 설정.
 *
 * 브라우저는 인증 헤더(Authorization)나 JSON 본문이 있는 요청을 보내기 전에 OPTIONS 사전 요청을 먼저 보내고,
 * 서버가 허용 헤더(Access-Control-Allow-*)로 답해야 실제 요청을 보낸다. 이 설정을 보안 필터(SecurityConfig의 cors)에
 * 연결하면 사전 요청은 로그인 확인보다 먼저 처리되어, OPTIONS를 따로 열어 주지 않아도 된다.
 *
 * 허용 주소는 동의 웹 한 곳이다. 다른 주소(예: 개발 중인 웹)를 더 허용하려면 설정 app.cors.allowed-origins에
 * 쉼표로 이어서 적는다(환경변수 APP_CORS_ALLOWED_ORIGINS). "*"처럼 모든 곳을 허용하지 않는다.
 * 앱(Android)은 브라우저가 아니라서 CORS와 상관없다. 서버 앞에 API Gateway 같은 CORS 설정이 따로 켜져 있다면
 * 헤더가 두 번 붙어 브라우저가 거부하니 한쪽만 쓴다(이 서버 설정을 권장한다).
 */
@Configuration
public class CorsConfig {

    /** 복약자 권한 관리 웹(GitHub Pages)의 주소 */
    static final String DEFAULT_ALLOWED_ORIGIN = "https://inhauniv-capstone-design.github.io";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGIN + "}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration(CorsOrigins.parse(allowedOrigins)));
        return source;
    }

    static CorsConfiguration configuration(List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 이 서버가 받는 헤더만 허용한다: 로그인 토큰, JSON 본문
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // 토큰은 Authorization 헤더로 보내고 쿠키는 쓰지 않는다
        configuration.setAllowCredentials(false);
        // 브라우저가 사전 요청 결과를 1시간 기억해서 매 요청마다 OPTIONS를 다시 보내지 않게 한다
        configuration.setMaxAge(3600L);
        return configuration;
    }
}