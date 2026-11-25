package com.gamcho.yakyeon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 휴대폰 번호(patient.phone_enc) 암복호화 전담 컴포넌트.
 * AES-256-GCM 사용 - IV를 매번 랜덤 생성해서 암호문 앞에 붙여 저장한다
 * (같은 번호를 암호화해도 매번 다른 결과가 나오게 해서, 암호문만으로 같은 사람인지
 * 추측 못 하게 함 - 동일인 식별은 phone_hash로만 가능).
 *
 * 키(encryption.phone-key)는 jwt.secret과 같은 방식으로 application-local.yml에
 * Base64 인코딩된 32바이트(256비트) 값으로 관리한다. 절대 커밋하지 말 것.
 */
@Component
public class PhoneEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PhoneEncryptor(@Value("${encryption.phone-key}") String base64Key) {
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
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("휴대폰 번호 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("휴대폰 번호 복호화에 실패했습니다.", e);
        }
    }
}