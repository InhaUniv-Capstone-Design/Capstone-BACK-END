package com.gamcho.yakyeon.domain.notification.push.fcm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** 시험용 서비스 계정 키(그때그때 만든 RSA 키)와 키 JSON을 만든다. 진짜 Firebase 키가 아니다 */
final class FcmTestSupport {

    static final String EMAIL = "firebase-adminsdk-test@yakyeon.iam.gserviceaccount.com";
    static final String KEY_ID = "test-key-id-1234";

    private FcmTestSupport() {
    }

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String pem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    /** Firebase 서비스 계정 키 파일과 같은 모양의 JSON (private_key 안의 줄바꿈은 \n 이스케이프) */
    static String keyJson(KeyPair pair, String tokenUri) {
        return "{\"type\":\"service_account\",\"project_id\":\"yakyeon\",\"private_key_id\":\"" + KEY_ID + "\",\"private_key\":\""
                + pem(pair).replace("\n", "\\n") + "\",\"client_email\":\"" + EMAIL + "\",\"client_id\":\"123\",\"token_uri\":\"" + tokenUri + "\"}";
    }
}