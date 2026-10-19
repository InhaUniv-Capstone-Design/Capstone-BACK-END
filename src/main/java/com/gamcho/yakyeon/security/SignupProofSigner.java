package com.gamcho.yakyeon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 회원가입용 "문자 인증 완료 증표" 발급·검증.
 *
 * 인증번호를 맞힌 사람에게만 서버가 서명한 증표를 주고, 가입할 때 그 증표를 같이 보내게 한다.
 * verification_id만 믿으면 남의 인증 번호(순차 정수)를 추측해서 그 번호로 가입할 수 있기 때문이다.
 * 증표는 (인증 ID, 번호 해시, 만료 시각)에 묶여 있어서 다른 번호나 다른 인증에는 쓸 수 없다.
 *
 * ⚠️ JWT를 쓰지 않는 이유: 액세스 토큰과 같은 형식·같은 키를 쓰면 증표가 액세스 토큰으로
 *    받아들여질 수 있다(인증 우회). 형식을 아예 다르게 하고 키도 용도 분리해서 파생한다.
 */
@Component
public class SignupProofSigner {

    private static final String HMAC = "HmacSHA256";
    private static final String KEY_PURPOSE = "yakyeon/signup-proof/v1";

    private final SecretKeySpec key;

    public SignupProofSigner(@Value("${jwt.secret}") String secret) {
        byte[] derived = hmac(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC), KEY_PURPOSE);
        this.key = new SecretKeySpec(derived, HMAC);
    }

    /** payload(인증 ID | 번호 해시 | 만료 epoch초) + "." + 서명 - 둘 다 Base64URL */
    public String issue(long verificationId, String phoneHash, Instant expiresAt) {
        String payload = verificationId + "|" + phoneHash + "|" + expiresAt.getEpochSecond();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(payloadBytes) + "." + enc.encodeToString(hmacBytes(payloadBytes));
    }

    /**
     * 서명·묶인 값·만료를 모두 확인한다. 실패 이유는 구분하지 않고 false만 돌려준다
     * (이유를 알려주면 증표를 위조하려는 쪽에 힌트가 된다).
     */
    public boolean verify(String token, long verificationId, String phoneHash, Instant now) {
        if (token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(token.substring(0, dot));
            byte[] signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));

            // 서명 비교는 일정 시간에 끝나는 방식으로 (비교 시간 차이로 서명을 추측하는 공격 방지)
            if (!MessageDigest.isEqual(hmacBytes(payloadBytes), signature)) {
                return false;
            }
            String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|");
            if (parts.length != 3) {
                return false;
            }
            return Long.parseLong(parts[0]) == verificationId
                    && parts[1].equals(phoneHash)
                    && now.getEpochSecond() <= Long.parseLong(parts[2]);
        } catch (IllegalArgumentException e) {
            // Base64 형식 오류, 숫자 형식 오류 (NumberFormatException도 IllegalArgumentException)
            return false;
        }
    }

    private byte[] hmacBytes(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다.", e);
        }
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