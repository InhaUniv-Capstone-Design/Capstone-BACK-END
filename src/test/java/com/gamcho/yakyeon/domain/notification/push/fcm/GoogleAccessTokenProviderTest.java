package com.gamcho.yakyeon.domain.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 가짜 구글 인증 서버를 이 컴퓨터에 띄워서 시험한다 (외부 연결과 진짜 Firebase 키가 필요 없다) */
class GoogleAccessTokenProviderTest {

    private static final KeyPair PAIR = FcmTestSupport.newKeyPair();

    private static final class FakeGoogle implements AutoCloseable {
        final HttpServer server;
        volatile int status = 200;
        volatile String body = "{\"access_token\":\"ya29.test-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";
        final AtomicInteger calls = new AtomicInteger();
        final List<String> methods = Collections.synchronizedList(new ArrayList<>());
        final List<String> contentTypes = Collections.synchronizedList(new ArrayList<>());
        final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

        FakeGoogle() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                calls.incrementAndGet();
                methods.add(exchange.getRequestMethod());
                contentTypes.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        String tokenUri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static GoogleAccessTokenProvider provider(FakeGoogle google, AtomicLong clock) {
        ServiceAccountKey key = ServiceAccountKey.parse(FcmTestSupport.keyJson(PAIR, google.tokenUri()), true);
        return new GoogleAccessTokenProvider(key, Duration.ofSeconds(3), Duration.ofSeconds(5), clock::get);
    }

    private static String reasonOf(GoogleAccessTokenProvider provider) {
        try {
            provider.accessToken();
        } catch (FcmAuthException e) {
            return e.reason();
        }
        return null;
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("구글 규격대로 요청한다: POST, 폼 형식, grant_type(jwt-bearer), assertion(JWT)")
    void sendsTheStandardRequest() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            String token = provider(google, new AtomicLong(1_700_000_000L)).accessToken();
            assertThat(token).isEqualTo("ya29.test-token");
            assertThat(google.methods.get(0)).isEqualTo("POST");
            assertThat(google.contentTypes.get(0)).isEqualTo("application/x-www-form-urlencoded");
            String form = google.bodies.get(0);
            assertThat(form.startsWith("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=")).isTrue();
            assertThat(form.split("&assertion=")[1].split("\\.").length).as("JWT는 세 부분").isEqualTo(3);
        }
    }

    @Test
    @DisplayName("JWT의 헤더·내용이 올바르고, 서명이 공개 키로 검증된다 (RS256, FCM 범위, 1시간)")
    void jwtIsCorrectlySigned() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            provider(google, new AtomicLong(1_700_000_000L)).accessToken();
            String[] parts = google.bodies.get(0).split("&assertion=")[1].split("\\.");
            Map<String, Object> header = MiniJson.asMap(MiniJson.parse(decode(parts[0])));
            Map<String, Object> claims = MiniJson.asMap(MiniJson.parse(decode(parts[1])));
            assertThat(MiniJson.asString(header.get("alg"))).isEqualTo("RS256");
            assertThat(MiniJson.asString(header.get("typ"))).isEqualTo("JWT");
            assertThat(MiniJson.asString(header.get("kid"))).isEqualTo(FcmTestSupport.KEY_ID);
            assertThat(MiniJson.asString(claims.get("iss"))).isEqualTo(FcmTestSupport.EMAIL);
            assertThat(MiniJson.asString(claims.get("scope"))).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
            assertThat(MiniJson.asString(claims.get("aud"))).isEqualTo(google.tokenUri());
            assertThat((long) MiniJson.asDouble(claims.get("iat"), 0)).isEqualTo(1_700_000_000L);
            assertThat((long) MiniJson.asDouble(claims.get("exp"), 0)).as("최대 1시간").isEqualTo(1_700_003_600L);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(PAIR.getPublic());
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).as("서명 검증").isTrue();
        }
    }

    @Test
    @DisplayName("토큰은 만료 5분 전까지 재사용하고, 그 뒤에는 새로 받는다")
    void cachesUntilNearExpiry() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            AtomicLong clock = new AtomicLong(1_000_000L);
            GoogleAccessTokenProvider provider = provider(google, clock);
            provider.accessToken();
            provider.accessToken();
            clock.addAndGet(3000);
            provider.accessToken();
            assertThat(google.calls.get()).as("50분 지나도 한 번만 받음").isEqualTo(1);
            clock.addAndGet(301);
            provider.accessToken();
            assertThat(google.calls.get()).as("만료 5분 전을 넘으면 새로 받음").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("FCM이 거절한 토큰은 무효화하면 다음에 새로 받는다")
    void invalidateForcesRefresh() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            GoogleAccessTokenProvider provider = provider(google, new AtomicLong(1_000_000L));
            provider.accessToken();
            provider.invalidate();
            provider.accessToken();
            assertThat(google.calls.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("동시에 여러 요청이 와도 토큰은 한 번만 받는다")
    void concurrentCallsFetchOnce() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            GoogleAccessTokenProvider provider = provider(google, new AtomicLong(1_000_000L));
            Thread[] threads = new Thread[8];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(provider::accessToken);
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }
            assertThat(google.calls.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("인증 실패는 원인만 알린다: 상태와 구글의 오류 이름(invalid_grant 등)")
    void authFailuresReportOnlyTheCause() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            GoogleAccessTokenProvider provider = provider(google, new AtomicLong(1_000_000L));
            google.status = 400;
            google.body = "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid JWT Signature.\"}";
            assertThat(reasonOf(provider)).isEqualTo("oauth-status=400,error=invalid_grant");
            google.status = 500;
            google.body = "oops";
            assertThat(reasonOf(provider)).isEqualTo("oauth-status=500");
            google.status = 200;
            google.body = "{\"token_type\":\"Bearer\"}";
            assertThat(reasonOf(provider)).isEqualTo("oauth-unexpected-response");
            google.body = "not json";
            assertThat(reasonOf(provider)).isEqualTo("oauth-unexpected-response");
        }
    }

    @Test
    @DisplayName("연결할 수 없으면 오류다")
    void connectionFailure() throws Exception {
        String uri;
        try (FakeGoogle google = new FakeGoogle()) {
            uri = google.tokenUri();
        }
        ServiceAccountKey key = ServiceAccountKey.parse(FcmTestSupport.keyJson(PAIR, uri), true);
        String reason = reasonOf(new GoogleAccessTokenProvider(key, Duration.ofSeconds(1), Duration.ofSeconds(1), () -> 1_000_000L));
        assertThat(reason != null && reason.startsWith("oauth-io-error")).as(reason).isTrue();
    }

    @Test
    @DisplayName("개인 키, JWT, 액세스 토큰은 어떤 오류에도 들어가지 않는다")
    void secretsNeverLeak() throws Exception {
        try (FakeGoogle google = new FakeGoogle()) {
            GoogleAccessTokenProvider provider = provider(google, new AtomicLong(1_000_000L));
            google.status = 401;
            google.body = "{\"error\":\"invalid_client\",\"access_token\":\"ya29.leak-me\"}";
            String reason = reasonOf(provider);
            assertThat(reason.contains("PRIVATE") || reason.contains("MII") || reason.contains("ya29") || reason.contains("eyJ")).as(reason).isFalse();
            String assertion = google.bodies.get(0).split("&assertion=")[1];
            assertThat(reason.contains(assertion)).isFalse();
        }
    }
}