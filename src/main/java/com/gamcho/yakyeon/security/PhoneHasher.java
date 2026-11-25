package com.gamcho.yakyeon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 휴대폰 번호 조회용 해시(patient.phone_hash, app_user.phone_hash) 전담 컴포넌트.
 *
 * 키 없는 SHA-256을 쓰면 안 되는 이유: 국내 휴대폰 번호는 경우의 수가 1억 개 남짓이라,
 * DB가 유출되면 모든 번호를 대입해서 해시에서 번호를 되살릴 수 있다.
 * 그래서 서버만 아는 키로 HMAC-SHA-256을 쓴다 - 키가 없으면 해시를 만들 수도, 되돌릴 수도 없다.
 * (같은 번호는 항상 같은 해시가 나와야 조회·중복 검사가 되므로 솔트 대신 키를 쓴다)
 *
 * 키는 encryption.phone-key에서 "용도 분리 문자열"로 파생해서 쓴다 - 암호화 키 원본을
 * 해시에 그대로 쓰지 않으므로, 설정값을 하나 더 늘리지 않고도 두 용도가 섞이지 않는다.
 * 번호는 반드시 PhoneNumbers.normalize()를 거친 숫자만 넣을 것.
 *
 * ⚠️ 키(encryption.phone-key)를 바꾸면 기존 해시가 전부 달라져서 번호 조회가 안 된다.
 *    배포 전에 키를 교체하고, 이미 쌓인 데이터가 있으면 복호화 후 다시 해시해야 한다.
 */
@Component
public class PhoneHasher {

    private static final String HMAC = "HmacSHA256";
    private static final String KEY_PURPOSE = "yakyeon/phone-hash/v1";

    private final SecretKeySpec hashKey;

    public PhoneHasher(@Value("${encryption.phone-key}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("encryption.phone-key는 올바른 Base64 문자열이어야 합니다.", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "encryption.phone-key는 Base64로 인코딩된 32바이트(256비트) 키여야 합니다. 현재 길이: " + keyBytes.length + "바이트");
        }
        byte[] derived = hmac(new SecretKeySpec(keyBytes, HMAC), KEY_PURPOSE);
        this.hashKey = new SecretKeySpec(derived, HMAC);
    }

    /** 정규화된(숫자만) 번호 → 64자리 소문자 hex. DB의 CHAR(64) 컬럼에 그대로 저장 */
    public String hash(String normalizedPhone) {
        return HexFormat.of().formatHex(hmac(hashKey, normalizedPhone));
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다.", e);
        }
    }
}