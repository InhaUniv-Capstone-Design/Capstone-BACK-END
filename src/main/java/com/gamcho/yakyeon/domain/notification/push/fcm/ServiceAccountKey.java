package com.gamcho.yakyeon.domain.notification.push.fcm;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Firebase 콘솔에서 받는 서비스 계정 키(JSON 파일)를 읽는다. 이 키가 있으면 우리 Firebase 프로젝트로 푸시를 보낼 수 있다.
 *
 * 비밀 보호: 개인 키(private_key)는 이 클래스 안에서만 쓰고(sign), toString·예외 메시지·로그에 남기지 않는다.
 * 형식이 틀려도 오류 메시지에는 파일 내용을 담지 않는다.
 *
 * token_uri는 https이고 구글 도메인(*.googleapis.com)이어야 한다(키로 서명한 인증 요청이 엉뚱한 곳으로 가지 않게).
 */
public final class ServiceAccountKey {

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final String projectId;
    private final String clientEmail;
    private final String privateKeyId;
    private final URI tokenUri;
    private final PrivateKey privateKey;

    private ServiceAccountKey(String projectId, String clientEmail, String privateKeyId, URI tokenUri, PrivateKey privateKey) {
        this.projectId = projectId;
        this.clientEmail = clientEmail;
        this.privateKeyId = privateKeyId;
        this.tokenUri = tokenUri;
        this.privateKey = privateKey;
    }

    public static ServiceAccountKey parse(String json) {
        return parse(json, false);
    }

    /** @param allowLocalTokenUri 시험에서만 true: token_uri로 이 컴퓨터의 가짜 인증 서버(http://127.0.0.1)를 허용 */
    static ServiceAccountKey parse(String json, boolean allowLocalTokenUri) {
        try {
            Map<String, Object> o = MiniJson.asMap(MiniJson.parse(json));
            if (o == null || !"service_account".equals(MiniJson.asString(o.get("type")))) {
                throw bad();
            }
            String clientEmail = required(o, "client_email");
            String privateKeyPem = required(o, "private_key");
            String projectId = MiniJson.asString(o.get("project_id"));
            String tokenUri = MiniJson.asString(o.get("token_uri"));
            return new ServiceAccountKey(projectId == null ? "" : projectId.trim(), clientEmail.trim(),
                    MiniJson.asString(o.get("private_key_id")), checkedTokenUri(tokenUri == null ? DEFAULT_TOKEN_URI : tokenUri.trim(),
                    allowLocalTokenUri), privateKeyOf(privateKeyPem));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw bad();
        }
    }

    private static IllegalArgumentException bad() {
        return new IllegalArgumentException("Firebase 서비스 계정 키 형식이 올바르지 않습니다.");
    }

    private static String required(Map<String, Object> o, String key) {
        String value = MiniJson.asString(o.get(key));
        if (value == null || value.isBlank()) {
            throw bad();
        }
        return value;
    }

    private static URI checkedTokenUri(String value, boolean allowLocal) {
        URI uri = URI.create(value);
        String host = uri.getHost();
        boolean google = "https".equals(uri.getScheme()) && host != null && host.endsWith(".googleapis.com") && uri.getUserInfo() == null;
        boolean local = allowLocal && "http".equals(uri.getScheme()) && "127.0.0.1".equals(host);
        if (!google && !local) {
            throw bad();
        }
        return uri;
    }

    private static PrivateKey privateKeyOf(String pem) throws GeneralSecurityException {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s+", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    public String projectId() {
        return projectId;
    }

    public String clientEmail() {
        return clientEmail;
    }

    public String privateKeyId() {
        return privateKeyId;
    }

    public URI tokenUri() {
        return tokenUri;
    }

    /** RS256(SHA256withRSA) 서명 */
    byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("서명에 실패했습니다.");
        }
    }

    @Override
    public String toString() {
        return "ServiceAccountKey[" + clientEmail + "]";   // 개인 키는 절대 담지 않는다
    }
}