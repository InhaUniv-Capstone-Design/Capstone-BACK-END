package com.gamcho.yakyeon.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsOriginsTest {

    @Test
    @DisplayName("주소 하나를 그대로 목록으로 만든다")
    void single() {
        assertThat(CorsOrigins.parse("https://inhauniv-capstone-design.github.io"))
                .isEqualTo(List.of("https://inhauniv-capstone-design.github.io"));
    }

    @Test
    @DisplayName("쉼표로 이은 여러 주소를 나누고 앞뒤 공백을 지운다")
    void multipleWithSpaces() {
        assertThat(CorsOrigins.parse(" https://a.example , http://localhost:5500 "))
                .isEqualTo(List.of("https://a.example", "http://localhost:5500"));
    }

    @Test
    @DisplayName("끝의 슬래시는 지운다 (브라우저가 보내는 주소에는 끝 슬래시가 없다)")
    void trailingSlashIsRemoved() {
        assertThat(CorsOrigins.parse("https://a.example/")).isEqualTo(List.of("https://a.example"));
    }

    @Test
    @DisplayName("빈 값과 중복은 버린다")
    void emptyAndDuplicates() {
        assertThat(CorsOrigins.parse("https://a.example,,  ,https://a.example/")).isEqualTo(List.of("https://a.example"));
    }

    @Test
    @DisplayName("설정이 비어 있으면 아무 곳도 허용하지 않는다")
    void emptyMeansNone() {
        assertThat(CorsOrigins.parse("").isEmpty()).isTrue();
        assertThat(CorsOrigins.parse("  ").isEmpty()).isTrue();
        assertThat(CorsOrigins.parse(null).isEmpty()).isTrue();
    }
}