package com.gamcho.yakyeon.domain.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceAccountKeyTest {

    private static final KeyPair PAIR = FcmTestSupport.newKeyPair();
    private static final String GOOGLE = "https://oauth2.googleapis.com/token";

    private static String rejectionMessage(String json, boolean local) {
        try {
            ServiceAccountKey.parse(json, local);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    @Test
    @DisplayName("Firebase 서비스 계정 키 파일을 읽는다 (프로젝트, 이메일, 키 번호, token_uri)")
    void parsesServiceAccountJson() {
        ServiceAccountKey key = ServiceAccountKey.parse(FcmTestSupport.keyJson(PAIR, GOOGLE));
        assertThat(key.projectId()).isEqualTo("yakyeon");
        assertThat(key.clientEmail()).isEqualTo(FcmTestSupport.EMAIL);
        assertThat(key.privateKeyId()).isEqualTo(FcmTestSupport.KEY_ID);
        assertThat(key.tokenUri().toString()).isEqualTo(GOOGLE);
    }

    @Test
    @DisplayName("token_uri가 없으면 구글의 기본 주소를 쓴다")
    void defaultTokenUri() {
        String json = FcmTestSupport.keyJson(PAIR, GOOGLE).replace(",\"token_uri\":\"" + GOOGLE + "\"", "");
        assertThat(ServiceAccountKey.parse(json).tokenUri().toString()).isEqualTo(GOOGLE);
    }

    @Test
    @DisplayName("개인 키로 한 서명이 공개 키로 검증된다 (RS256)")
    void signatureVerifies() throws Exception {
        ServiceAccountKey key = ServiceAccountKey.parse(FcmTestSupport.keyJson(PAIR, GOOGLE));
        byte[] data = "header.claims".getBytes(StandardCharsets.US_ASCII);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(PAIR.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(key.sign(data))).isTrue();
    }

    @Test
    @DisplayName("형식이 틀린 키는 거절한다")
    void rejectsMalformed() {
        String good = FcmTestSupport.keyJson(PAIR, GOOGLE);
        String[] bad = {"", "not json", "[]", "{}", good.replace("service_account", "authorized_user"),
                good.replace("\"client_email\":\"" + FcmTestSupport.EMAIL + "\",", ""),
                good.replaceAll("\"private_key\":\"[^\"]*\",", ""),
                good.replaceAll("\"private_key\":\"[^\"]*\"", "\"private_key\":\"not-a-pem\"")};
        for (String json : bad) {
            assertThat(rejectionMessage(json, false) != null).as("거절해야 함: " + json.substring(0, Math.min(40, json.length()))).isTrue();
        }
    }

    @Test
    @DisplayName("token_uri는 https이고 구글 도메인이어야 한다 (서명한 인증 요청이 엉뚱한 곳으로 가지 않게)")
    void tokenUriMustBeGoogle() {
        for (String uri : new String[]{"http://oauth2.googleapis.com/token", "https://evil.example.com/token", "https://oauth2.googleapis.com.evil.com/token",
                "https://user:pw@oauth2.googleapis.com/token", "http://127.0.0.1:9999/token"}) {
            assertThat(rejectionMessage(FcmTestSupport.keyJson(PAIR, uri), false) != null).as("거절: " + uri).isTrue();
        }
        assertThat(rejectionMessage(FcmTestSupport.keyJson(PAIR, "http://127.0.0.1:9999/token"), true)).as("시험용 로컬 주소는 시험에서만").isEqualTo(null);
    }

    @Test
    @DisplayName("오류 메시지와 toString에 개인 키가 들어가지 않는다")
    void neverLeaksThePrivateKey() {
        String json = FcmTestSupport.keyJson(PAIR, GOOGLE).replaceAll("\"private_key\":\"[^\"]*\"", "\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nMIIEvQSECRET\\n-----END PRIVATE KEY-----\\n\"");
        String message = rejectionMessage(json, false);
        assertThat(message != null && !message.contains("SECRET") && !message.contains("MIIEv")).isTrue();
        ServiceAccountKey key = ServiceAccountKey.parse(FcmTestSupport.keyJson(PAIR, GOOGLE));
        assertThat(key.toString().contains("PRIVATE") || key.toString().contains("MII")).isFalse();
    }
}