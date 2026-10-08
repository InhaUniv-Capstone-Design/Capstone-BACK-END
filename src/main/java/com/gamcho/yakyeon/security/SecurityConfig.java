package com.gamcho.yakyeon.security;

import com.gamcho.yakyeon.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 공개 엔드포인트(signup/check-id/login/refresh/logout, GET /terms, POST /revocations/*)만 permitAll, 나머지는 인증 필요.
 * httpBasic/formLogin을 꺼서 기본 AuthenticationEntryPoint가 없어졌던 걸
 * JwtAuthenticationEntryPoint로 직접 등록해서, 인증 안 된 요청이
 * 403(빈 바디) 대신 401 + 공통 에러 포맷으로 나가게 한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // JWT는 헤더로 전달되고 세션 쿠키를 쓰지 않으므로 CSRF 보호 대상이 아님(REST API 표준 관행)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // 세션을 만들지 않는다 - 모든 인증 상태는 매 요청의 Access Token으로만 판단(무상태)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // 공개 엔드포인트만 명시적으로 허용 - "/auth/**" 블랭킷 허용 금지
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/check-id").permitAll()
                        // 약관 본문 조회 - 회원가입 화면에서 로그인 전에 호출해야 하므로 공개
                        .requestMatchers(HttpMethod.GET, "/terms").permitAll()
                        // 문자 인증 철회 - 앱 계정이 없는 복약자는 로그인할 수 없으므로 공개
                        .requestMatchers(HttpMethod.POST, "/revocations/request", "/revocations/confirm").permitAll()
                        // 앞으로 patient/medication 등 새 컨트롤러를 추가해도 기본은 "인증 필요" -
                        // 개별 엔드포인트를 열어주려면 여기에 명시적으로 추가해야 하므로,
                        // "깜빡하고 인증 없이 노출"되는 실수를 구조적으로 막는다.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}