package com.gamcho.yakyeon.security;

import java.util.Arrays;
import java.util.List;

/** 설정으로 받은 "허용할 웹 주소" 문자열을 목록으로 바꾼다 (스프링과 무관한 순수 로직). */
final class CorsOrigins {

    private CorsOrigins() {
    }

    /**
     * 쉼표로 구분된 주소를 목록으로 만든다. 앞뒤 공백, 빈 값, 끝의 슬래시(/)를 정리한다
     * (브라우저가 보내는 Origin에는 끝 슬래시가 없어서, 남아 있으면 허용 주소와 영원히 맞지 않는다).
     * 비어 있으면 빈 목록이다. 아무 곳도 허용하지 않는다는 뜻이다.
     */
    static List<String> parse(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }
}