package com.gamcho.yakyeon.domain.device.logic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 기기 비밀값의 해시와 확인 (스프링 없는 순수 로직).
 *
 * 기기 비밀값은 기기 제조 때 발급하는 길고 무작위인 값(256비트 수준)이라, 비밀번호처럼 느린 해시가 아니라
 * 빠른 SHA-256으로 충분하다 (기기가 신호를 보낼 때마다 확인해야 해서 빨라야 한다). 로그인 토큰과 같은 방식이다.
 * 서버에는 해시만 저장하고 원문은 저장하지 않는다.
 */
public final class DeviceSecrets {

    /** 등록되지 않은 기기에도 같은 계산을 하게 해서, 응답 시간으로 기기 존재 여부를 알 수 없게 하는 자리표시 해시 */
    public static final String DUMMY_HASH = hash("no-such-device-placeholder-secret-0000000000");

    private DeviceSecrets() {
    }

    /** SHA-256 소문자 16진수 (64자) */
    public static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    /** 받은 비밀값이 저장된 해시와 같은지. 일치하는 길이까지 비교 시간이 같도록 상수 시간 비교를 쓴다 */
    public static boolean matches(String secret, String storedHash) {
        if (secret == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(secret).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}