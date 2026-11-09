package com.gamcho.yakyeon.domain.notification.push.fcm;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 서비스 계정 키로 구글에서 FCM용 액세스 토큰을 받아 둔다 (구글 OAuth 2.0 서비스 계정 방식).
 *
 *  1) JWT를 만든다: 헤더 {alg:RS256, typ:JWT}, 내용 {iss:서비스 계정 이메일, scope:FCM 범위, aud:token_uri, iat, exp(최대 1시간)}를
 *     개인 키로 서명한다.
 *  2) token_uri에 POST(grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer, assertion=JWT)하면 액세스 토큰(약 1시간 유효)이 온다.
 *  3) 만료 5분 전까지 같은 토큰을 재사용하고, 그 뒤에는 새로 받는다.
 *
 * 여러 요청이 동시에 와도 토큰을 한 번만 받도록 동기화한다. 개인 키, JWT, 액세스 토큰은 로그와 예외에 남기지 않는다.
 */
public class GoogleAccessTokenProvider {

    static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final long REFRESH_MARGIN_SECONDS = 300;
    private static final long JWT_LIFETIME_SECONDS = 3600;
    private static final int MAX_RESPONSE_BYTES = 64_000;

    private final ServiceAccountKey key;
    private final HttpClient http;
    private final Duration readTimeout;
    private final LongSupplier nowSeconds;

    private String accessToken;
    private long expiresAt;

    public GoogleAccessTokenProvider(ServiceAccountKey key, Duration connectTimeout, Duration readTimeout) {
        this(key, connectTimeout, readTimeout, () -> Instant.now().getEpochSecond());
    }

    GoogleAccessTokenProvider(ServiceAccountKey key, Duration connectTimeout, Duration readTimeout, LongSupplier nowSeconds) {
        this.key = key;
        this.readTimeout = readTimeout;
        this.nowSeconds = nowSeconds;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).build();
    }

    /** 유효한 액세스 토큰. 필요하면 새로 받는다 @throws FcmAuthException 받지 못함 */
    public synchronized String accessToken() {
        long now = nowSeconds.getAsLong();
        if (accessToken == null || now >= expiresAt - REFRESH_MARGIN_SECONDS) {
            fetch(now);
        }
        return accessToken;
    }

    /** 이 토큰은 더 못 쓴다(FCM이 401로 거절했다). 다음 호출에서 새로 받는다 */
    public synchronized void invalidate() {
        accessToken = null;
    }

    private void fetch(long now) {
        String assertion = signedJwt(now);
        String form = "grant_type=" + "urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer" + "&assertion=" + assertion;
        try {
            HttpRequest request = HttpRequest.newBuilder(key.tokenUri()).timeout(readTimeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (java.io.InputStream in = response.body()) {
                body = new String(in.readNBytes(MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
            }
            Map<String, Object> json = parse(body);
            if (response.statusCode() != 200) {
                throw new FcmAuthException("oauth-status=" + response.statusCode() + errorSuffix(json));
            }
            String token = json == null ? null : MiniJson.asString(json.get("access_token"));
            if (token == null || token.isBlank()) {
                throw new FcmAuthException("oauth-unexpected-response");
            }
            this.accessToken = token;
            this.expiresAt = now + (long) MiniJson.asDouble(json == null ? null : json.get("expires_in"), 3600);
        } catch (IOException e) {
            throw new FcmAuthException("oauth-io-error:" + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FcmAuthException("oauth-interrupted");
        }
    }

    private String signedJwt(long now) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"" + (key.privateKeyId() == null ? "" : ",\"kid\":" + FcmJson.quote(key.privateKeyId())) + "}";
        String claims = "{\"iss\":" + FcmJson.quote(key.clientEmail()) + ",\"scope\":\"" + FCM_SCOPE + "\",\"aud\":" + FcmJson.quote(key.tokenUri().toString())
                + ",\"iat\":" + now + ",\"exp\":" + (now + JWT_LIFETIME_SECONDS) + "}";
        String signingInput = b64.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "." + b64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + b64.encodeToString(key.sign(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    private static Map<String, Object> parse(String body) {
        try {
            return MiniJson.asMap(MiniJson.parse(body));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 구글의 오류 이름(예: invalid_grant)만 꺼낸다. 소문자와 밑줄 40자 이내일 때만 */
    private static String errorSuffix(Map<String, Object> json) {
        String error = json == null ? null : MiniJson.asString(json.get("error"));
        return error != null && error.matches("[a-z_]{1,40}") ? ",error=" + error : "";
    }

    URI tokenUriForTest() {
        return key.tokenUri();
    }
}