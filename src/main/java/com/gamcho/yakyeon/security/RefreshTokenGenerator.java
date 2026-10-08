package com.gamcho.yakyeon.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Refresh Token 원문 생성.
 * UUID(122비트)보다 안전한 256비트 SecureRandom을 사용한다.
 * 이 값은 클라이언트에게만 전달되고, DB에는 TokenHasher로 해시한 값만 저장한다.
 */
@Component
public class RefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256비트

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}