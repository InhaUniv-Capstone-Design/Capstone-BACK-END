package com.gamcho.yakyeon.security.jwt;

import com.gamcho.yakyeon.domain.account.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Access Token(JWT) 발급/검증.
 * Refresh Token은 여기서 다루지 않는다 - 그건 DB(auth_token)에 해시로 저장되는 별도 체계.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationSeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-seconds:1800}") long accessTokenExpirationSeconds) {

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256은 256비트(32바이트) 이상의 키가 필요하다. 짧은 키로 서명하면
            // 토큰 위조가 현실적으로 가능해지므로, 약한 설정으로 기동되는 것 자체를 막는다.
            throw new IllegalStateException(
                    "jwt.secret은 최소 32바이트(256비트) 이상이어야 합니다. 현재 길이: " + secretBytes.length + "바이트");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public String createAccessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("account_type", user.getAccountType().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationSeconds)))
                .signWith(key)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    /**
     * 서명 검증 + 만료 검증까지 포함해서 클레임을 반환한다.
     * 서명이 틀렸거나 만료됐으면 io.jsonwebtoken.JwtException 계열 예외가 던져진다
     * (호출부에서 잡아서 401로 변환할 것 - 아직 이 필터/핸들러는 별도로 안 만들었으니
     * 로그인/리프레시 발급에만 우선 쓰고, 보호된 API를 만들 때 이 메서드로 필터를 구현하면 된다).
     */
    public Claims parseClaims(String accessToken) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
    }

    public Long getUserId(String accessToken) {
        return Long.valueOf(parseClaims(accessToken).getSubject());
    }
}