package com.gamcho.yakyeon.domain.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;
import com.gamcho.yakyeon.domain.notification.push.PushMessage;
import com.gamcho.yakyeon.domain.notification.push.PushResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 가짜 구글 인증 서버와 가짜 FCM 서버를 이 컴퓨터에 띄워 발송기를 끝까지 시험한다 */
class FcmPushSenderTest {

    private static final KeyPair PAIR = FcmTestSupport.newKeyPair();
    private static final String DEVICE_TOKEN = "device-token-abcdefghijklmnopqrstuvwxyz:0123456789";
    private static final PushMessage MESSAGE = new PushMessage(DEVICE_TOKEN, "복용 알림", "아침 약을 복용할 시간입니다.", "INTAKE", 12345L);
    private static final String OK = "{\"name\":\"projects/yakyeon/messages/0:99%abc\"}";

    /** 한 서버에 인증(/token)과 발송(/v1/projects/yakyeon/messages:send)을 모두 둔다 */
    private static final class Fake implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger tokenCalls = new AtomicInteger();
        final AtomicInteger fcmCalls = new AtomicInteger();
        volatile int tokenStatus = 200;
        volatile String tokenError = "";
        volatile long fcmDelayMillis = 0;
        /** 발송 요청마다 차례로 돌려줄 응답 (다 쓰면 마지막 것을 반복) */
        final Deque<Object[]> fcmResponses = new ArrayDeque<>();
        final List<String> authHeaders = Collections.synchronizedList(new ArrayList<>());
        final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        final List<String> contentTypes = Collections.synchronizedList(new ArrayList<>());

        Fake() throws IOException {
            fcmResponses.add(new Object[]{200, OK});
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                exchange.getRequestBody().readAllBytes();
                int n = tokenCalls.incrementAndGet();
                String body = tokenStatus == 200 ? "{\"access_token\":\"ya29.tok" + n + "\",\"expires_in\":3600}" : tokenError;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(tokenStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/v1/projects/yakyeon/messages:send", exchange -> {
                fcmCalls.incrementAndGet();
                authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                contentTypes.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try {
                    if (fcmDelayMillis > 0) {
                        Thread.sleep(fcmDelayMillis);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                Object[] response;
                synchronized (fcmResponses) {
                    response = fcmResponses.size() > 1 ? fcmResponses.poll() : fcmResponses.peek();
                }
                byte[] bytes = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders((Integer) response[0], bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        String base() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void respond(Object... pairs) {
            synchronized (fcmResponses) {
                fcmResponses.clear();
                for (int i = 0; i < pairs.length; i += 2) {
                    fcmResponses.add(new Object[]{pairs[i], pairs[i + 1]});
                }
            }
        }

        FcmPushSender sender(long readMillis) {
            ServiceAccountKey key = ServiceAccountKey.parse(FcmTestSupport.keyJson(PAIR, base() + "/token"), true);
            GoogleAccessTokenProvider provider = new GoogleAccessTokenProvider(key, Duration.ofSeconds(3), Duration.ofSeconds(5));
            return new FcmPushSender(provider, URI.create(base() + "/v1/projects/yakyeon/messages:send"), 3600, Duration.ofSeconds(3), Duration.ofMillis(readMillis));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static String fcmError(int code, String status, String fcmCode, String message) {
        String details = fcmCode == null ? "" : ",\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"" + fcmCode + "\"}]";
        return "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\",\"status\":\"" + status + "\"" + details + "}}";
    }

    @Test
    @DisplayName("성공: SENT와 메시지 번호, 요청은 Bearer 인증과 약속한 JSON 본문이다")
    void sendsMessageInTheAgreedFormat() throws Exception {
        try (Fake fake = new Fake()) {
            PushResult result = fake.sender(5000).send(MESSAGE);
            assertThat(result.status()).isEqualTo(PushResult.Status.SENT);
            assertThat(result.messageId()).isEqualTo("0:99%abc");
            assertThat(fake.authHeaders.get(0)).isEqualTo("Bearer ya29.tok1");
            assertThat(fake.contentTypes.get(0)).isEqualTo("application/json; charset=UTF-8");
            Map<String, Object> message = MiniJson.asMap(MiniJson.asMap(MiniJson.parse(fake.bodies.get(0))).get("message"));
            assertThat(MiniJson.asString(message.get("token"))).isEqualTo(DEVICE_TOKEN);
            Map<String, Object> data = MiniJson.asMap(message.get("data"));
            assertThat(MiniJson.asString(data.get("title"))).isEqualTo("복용 알림");
            assertThat(MiniJson.asString(data.get("body"))).isEqualTo("아침 약을 복용할 시간입니다.");
            assertThat(MiniJson.asString(data.get("notification_id"))).isEqualTo("12345");
            assertThat(MiniJson.asString(MiniJson.asMap(message.get("android")).get("priority"))).isEqualTo("high");
        }
    }

    @Test
    @DisplayName("액세스 토큰은 한 번 받아서 여러 번 보낼 때 재사용한다")
    void reusesAccessToken() throws Exception {
        try (Fake fake = new Fake()) {
            FcmPushSender sender = fake.sender(5000);
            sender.send(MESSAGE);
            sender.send(MESSAGE);
            sender.send(MESSAGE);
            assertThat(fake.tokenCalls.get()).isEqualTo(1);
            assertThat(fake.fcmCalls.get()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("UNREGISTERED(404)는 INVALID_TOKEN: 발송 기록 쪽이 이 토큰을 지운다")
    void unregisteredTokenIsReported() throws Exception {
        try (Fake fake = new Fake()) {
            fake.respond(404, fcmError(404, "NOT_FOUND", "UNREGISTERED", "Requested entity was not found."));
            assertThat(fake.sender(5000).send(MESSAGE).status()).isEqualTo(PushResult.Status.INVALID_TOKEN);
        }
    }

    @Test
    @DisplayName("한도 초과, 서버 바쁨, 내부 오류는 일시 실패다 (나중에 다시 보낸다)")
    void transientFailures() throws Exception {
        try (Fake fake = new Fake()) {
            FcmPushSender sender = fake.sender(5000);
            fake.respond(429, fcmError(429, "RESOURCE_EXHAUSTED", "QUOTA_EXCEEDED", "Quota exceeded"));
            PushResult quota = sender.send(MESSAGE);
            assertThat(quota.status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
            assertThat(quota.errorCode()).isEqualTo("QUOTA_EXCEEDED");
            fake.respond(503, fcmError(503, "UNAVAILABLE", "UNAVAILABLE", "The service is currently unavailable."));
            assertThat(sender.send(MESSAGE).errorCode()).isEqualTo("UNAVAILABLE");
            fake.respond(500, "oops");
            assertThat(sender.send(MESSAGE).errorCode()).isEqualTo("HTTP_500");
        }
    }

    @Test
    @DisplayName("SENDER_ID_MISMATCH와 우리 요청 형식 오류(INVALID_ARGUMENT)는 토큰을 지우지 않는다")
    void configurationErrorsKeepTokens() throws Exception {
        try (Fake fake = new Fake()) {
            FcmPushSender sender = fake.sender(5000);
            fake.respond(403, fcmError(403, "PERMISSION_DENIED", "SENDER_ID_MISMATCH", "SenderId mismatch"));
            assertThat(sender.send(MESSAGE).status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
            fake.respond(400, fcmError(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT", "Invalid value at 'message.data[0].value'"));
            assertThat(sender.send(MESSAGE).status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
            fake.respond(400, fcmError(400, "INVALID_ARGUMENT", null, "The registration token is not a valid FCM registration token"));
            assertThat(sender.send(MESSAGE).status()).as("토큰 형식 오류는 무효 토큰").isEqualTo(PushResult.Status.INVALID_TOKEN);
        }
    }

    @Test
    @DisplayName("액세스 토큰이 만료돼 401이면 새로 받아서 한 번 더 보낸다")
    void refreshesTokenOn401() throws Exception {
        try (Fake fake = new Fake()) {
            fake.respond(401, fcmError(401, "UNAUTHENTICATED", null, "Request had invalid authentication credentials."), 200, OK);
            PushResult result = fake.sender(5000).send(MESSAGE);
            assertThat(result.status()).isEqualTo(PushResult.Status.SENT);
            assertThat(fake.tokenCalls.get()).isEqualTo(2);
            assertThat(fake.authHeaders.get(0)).isEqualTo("Bearer ya29.tok1");
            assertThat(fake.authHeaders.get(1)).as("두 번째는 새 토큰").isEqualTo("Bearer ya29.tok2");
        }
    }

    @Test
    @DisplayName("401이 계속되면 한 번만 다시 시도하고 일시 실패로 끝낸다 (무한 반복하지 않는다)")
    void persistent401DoesNotLoop() throws Exception {
        try (Fake fake = new Fake()) {
            fake.respond(401, fcmError(401, "UNAUTHENTICATED", null, "bad"));
            PushResult result = fake.sender(5000).send(MESSAGE);
            assertThat(result.status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
            assertThat(result.errorCode()).isEqualTo("UNAUTHENTICATED");
            assertThat(fake.fcmCalls.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("구글 인증에 실패하면 FCM에는 보내지 않고 AUTH_FAILED로 일시 실패 처리한다")
    void authFailureDoesNotCallFcm() throws Exception {
        try (Fake fake = new Fake()) {
            fake.tokenStatus = 400;
            fake.tokenError = "{\"error\":\"invalid_grant\"}";
            PushResult result = fake.sender(5000).send(MESSAGE);
            assertThat(result.status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
            assertThat(result.errorCode()).isEqualTo("AUTH_FAILED");
            assertThat(fake.fcmCalls.get()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("응답이 늦으면 NETWORK로 일시 실패 처리한다")
    void slowFcmIsTransient() throws Exception {
        try (Fake fake = new Fake()) {
            fake.fcmDelayMillis = 1500;
            PushResult result = fake.sender(300).send(MESSAGE);
            assertThat(result.status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
            assertThat(result.errorCode()).isEqualTo("NETWORK");
        }
    }

    @Test
    @DisplayName("결과(오류 이름, 메시지 번호)에 기기 토큰, 알림 문구, 액세스 토큰이 들어가지 않는다")
    void resultsNeverContainSecrets() throws Exception {
        try (Fake fake = new Fake()) {
            FcmPushSender sender = fake.sender(5000);
            String[] bodies = {fcmError(404, "NOT_FOUND", "UNREGISTERED", DEVICE_TOKEN), fcmError(500, "INTERNAL", "INTERNAL", "복용 알림 ya29.tok1"), "복용 알림 " + DEVICE_TOKEN};
            int[] codes = {404, 500, 502};
            for (int i = 0; i < codes.length; i++) {
                fake.respond(codes[i], bodies[i]);
                PushResult r = sender.send(MESSAGE);
                String text = r.errorCode() + "|" + r.messageId();
                assertThat(text.contains(DEVICE_TOKEN) || text.contains("복용 알림") || text.contains("ya29")).as(text).isFalse();
            }
        }
    }

    @Test
    @DisplayName("한글, 따옴표, 줄바꿈이 든 문구도 서버에 그대로 도착한다")
    void unusualTextArrivesIntact() throws Exception {
        try (Fake fake = new Fake()) {
            String body = "\"약\" 먹을 시간\n😀";
            fake.sender(5000).send(new PushMessage(DEVICE_TOKEN, "제목\\", body, "X", 1L));
            Map<String, Object> data = MiniJson.asMap(MiniJson.asMap(MiniJson.asMap(MiniJson.parse(fake.bodies.get(0))).get("message")).get("data"));
            assertThat(MiniJson.asString(data.get("title"))).isEqualTo("제목\\");
            assertThat(MiniJson.asString(data.get("body"))).isEqualTo(body);
        }
    }

    @Test
    @DisplayName("발송 주소는 프로젝트 번호로 만든다")
    void sendUriFormat() {
        assertThat(FcmPushSender.sendUri("https://fcm.googleapis.com", "yakyeon").toString()).isEqualTo("https://fcm.googleapis.com/v1/projects/yakyeon/messages:send");
        assertThat(FcmPushSender.sendUri("https://fcm.googleapis.com/", "yakyeon").toString()).isEqualTo("https://fcm.googleapis.com/v1/projects/yakyeon/messages:send");
    }
}