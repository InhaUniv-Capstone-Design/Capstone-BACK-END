package com.gamcho.yakyeon.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;

/** 동의 웹만 허용하고 그 밖의 주소·메서드·헤더는 허용하지 않는지 확인한다 (스프링 CORS 설정 객체만 검사, 서버를 켜지 않는다). */
class CorsConfigTest {

    private static final String WEB = "https://inhauniv-capstone-design.github.io";

    private static CorsConfiguration config() {
        return CorsConfig.configuration(List.of(WEB));
    }

    @Test
    @DisplayName("동의 웹 주소는 허용하고, 다른 주소는 허용하지 않는다")
    void onlyTheConsentWebIsAllowed() {
        assertThat(config().checkOrigin(WEB)).isEqualTo(WEB);
        assertThat(config().checkOrigin("https://evil.example") == null).isTrue();
        assertThat(config().checkOrigin("http://inhauniv-capstone-design.github.io") == null).as("http는 다른 주소").isTrue();
        assertThat(config().checkOrigin("null") == null).as("Origin: null(파일에서 연 페이지 등)").isTrue();
    }

    @Test
    @DisplayName("모든 주소(*)를 허용하지 않는다")
    void noWildcardOrigin() {
        assertThat(config().getAllowedOrigins().contains("*")).isFalse();
        assertThat(config().getAllowedOriginPatterns() == null || config().getAllowedOriginPatterns().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("이 서버가 쓰는 메서드(조회, 등록, 수정, 삭제)는 허용하고 TRACE 같은 것은 허용하지 않는다")
    void methods() {
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                HttpMethod.DELETE, HttpMethod.OPTIONS)) {
            assertThat(config().checkHttpMethod(method) != null).as(method.name()).isTrue();
        }
        assertThat(config().checkHttpMethod(HttpMethod.TRACE) == null).isTrue();
    }

    @Test
    @DisplayName("로그인 토큰과 JSON 본문 헤더는 허용하고 그 밖의 헤더는 허용하지 않는다")
    void headers() {
        assertThat(config().checkHeaders(List.of("authorization", "content-type")) != null).isTrue();
        assertThat(config().checkHeaders(List.of("x-custom-evil")) == null).isTrue();
    }

    @Test
    @DisplayName("쿠키 같은 자격 증명은 허용하지 않고, 사전 요청 결과는 1시간 기억시킨다")
    void credentialsAndMaxAge() {
        assertThat(config().getAllowCredentials() == null || !config().getAllowCredentials()).isTrue();
        assertThat(config().getMaxAge()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("허용 주소가 하나도 없으면 어떤 웹도 허용하지 않는다")
    void noOriginsMeansNoneAllowed() {
        assertThat(CorsConfig.configuration(List.of()).checkOrigin(WEB) == null).isTrue();
    }
}