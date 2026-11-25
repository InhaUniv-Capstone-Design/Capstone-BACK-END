package com.gamcho.yakyeon.client.octomo;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가짜 OCTOMO 서버를 이 컴퓨터에 실제로 띄워서 HTTP로 시험한다(외부 연결 없음, API Key 필요 없음).
 * 요청의 모양(헤더, 본문), 오류 응답, 시간 초과, 호출 한도, 동시 호출 제한, 리다이렉트를 확인한다.
 */
class OctomoHttpClientTest {

    private static final String KEY = "test-api-key-1234567890";
    private static final String PHONE = "01012345678";

    /** 받은 요청을 기록하고 정해 둔 응답을 돌려주는 가짜 서버 */
    private static final class FakeOctomo implements AutoCloseable {
        final HttpServer server;
        volatile int status = 201;
        volatile String body = "{\"exists\":true}";
        volatile long delayMillis = 0;
        volatile String location = null;
        final AtomicInteger calls = new AtomicInteger();
        final List<String> methods = Collections.synchronizedList(new ArrayList<>());
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
        final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
        final List<String> contentTypes = Collections.synchronizedList(new ArrayList<>());
        final List<String> accepts = Collections.synchronizedList(new ArrayList<>());
        final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

        FakeOctomo() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                methods.add(exchange.getRequestMethod());
                paths.add(exchange.getRequestURI().getPath());
                authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                contentTypes.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
                accepts.add(String.valueOf(exchange.getRequestHeaders().getFirst("Accept")));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try {
                    if (delayMillis > 0) {
                        Thread.sleep(delayMillis);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                if (location != null) {
                    exchange.getResponseHeaders().add("Location", location);
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/octomo/v1";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static OctomoHttpClient client(FakeOctomo fake) {
        return client(fake, 500, 10, 3000);
    }

    private static OctomoHttpClient client(FakeOctomo fake, int dailyLimit, int maxConcurrent, long readTimeoutMs) {
        return new OctomoHttpClient(KEY, fake.baseUrl(), "16663538", Duration.ofMillis(1000),
                Duration.ofMillis(readTimeoutMs), maxConcurrent, dailyLimit);
    }

    /** 호출했더니 OctomoUnavailableException이 났다면 그 메시지, 아니면 null */
    private static String unavailable(Runnable call) {
        try {
            call.run();
            return null;
        } catch (OctomoUnavailableException e) {
            return e.getMessage();
        }
    }

    private static boolean rejectsArguments(Runnable call) {
        try {
            call.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 문자 조회

    @Test
    @DisplayName("조회 요청의 모양: POST, 정해진 주소, Authorization 'Octomo 키', JSON 본문 (번호, 문구, 조회 범위)")
    void existsRequestShape() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            boolean exists = client(fake).messageExists(PHONE, "123456", 5);

            assertThat(exists).isTrue();
            assertThat(fake.calls.get()).isEqualTo(1);
            assertThat(fake.methods.get(0)).isEqualTo("POST");
            assertThat(fake.paths.get(0)).isEqualTo("/octomo/v1/public/message/exists");
            assertThat(fake.authorizations.get(0)).isEqualTo("Octomo " + KEY);
            assertThat(fake.contentTypes.get(0)).isEqualTo("application/json");
            assertThat(fake.accepts.get(0)).isEqualTo("application/json");
            assertThat(fake.bodies.get(0)).isEqualTo("{\"mobileNum\":\"01012345678\",\"text\":\"123456\",\"withinMinutes\":5}");
        }
    }

    @Test
    @DisplayName("exists가 false면 false (문자가 아직 안 온 것)")
    void existsFalse() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            fake.body = "{\"exists\":false}";
            assertThat(client(fake).messageExists(PHONE, "123456", 1)).isFalse();
        }
    }

    @Test
    @DisplayName("응답에 다른 필드가 더 있어도 exists만 읽는다")
    void ignoresUnknownFields() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            fake.body = "{ \"extra\": 1, \"exists\" : true, \"more\": \"x\" }";
            assertThat(client(fake).messageExists(PHONE, "123456", 5)).isTrue();
        }
    }

    @Test
    @DisplayName("오류 응답(400, 401, 403, 404, 429, 500)과 리다이렉트(302)는 상태 코드만 담은 오류가 되고 API Key는 안 담긴다")
    void errorStatusesBecomeUnavailable() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            OctomoHttpClient client = client(fake);
            for (int status : new int[]{400, 401, 403, 404, 429, 500, 302}) {
                fake.status = status;
                fake.body = "{\"statusCode\":" + status + ",\"message\":\"Invalid API Key\",\"error\":\"" + KEY + "\"}";
                String message = unavailable(() -> client.messageExists(PHONE, "123456", 5));
                assertThat(message).as("상태 " + status).isEqualTo("status=" + status);
                assertThat(message.contains(KEY)).as("키가 메시지에 없어야 함").isFalse();
            }
        }
    }

    @Test
    @DisplayName("모양이 이상한 응답(필드 없음, HTML)은 오류다")
    void malformedBodyBecomesUnavailable() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            OctomoHttpClient client = client(fake);
            for (String body : new String[]{"{\"foo\":1}", "<html>점검 중</html>", "", "{\"exists\":\"yes\"}"}) {
                fake.body = body;
                assertThat(unavailable(() -> client.messageExists(PHONE, "123456", 5))).as(body).isEqualTo("unexpected-response");
            }
        }
    }

    @Test
    @DisplayName("너무 큰 응답은 오류다")
    void tooLargeResponse() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            fake.body = "{\"exists\":true,\"pad\":\"" + "x".repeat(310_000) + "\"}";
            assertThat(unavailable(() -> client(fake).messageExists(PHONE, "123456", 5))).isEqualTo("response-too-large");
        }
    }

    @Test
    @DisplayName("응답이 늦으면 시간 초과로 오류다")
    void readTimeout() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            fake.delayMillis = 1500;
            OctomoHttpClient client = client(fake, 500, 10, 300);
            String message = unavailable(() -> client.messageExists(PHONE, "123456", 5));
            assertThat(message).isEqualTo("io-error:HttpTimeoutException");
        }
    }

    @Test
    @DisplayName("서버에 연결할 수 없으면 오류다")
    void connectionRefused() throws Exception {
        FakeOctomo fake = new FakeOctomo();
        OctomoHttpClient client = client(fake);
        fake.close();
        String message = unavailable(() -> client.messageExists(PHONE, "123456", 5));
        assertThat(message == null).as("오류가 나야 함").isFalse();
        assertThat(message.startsWith("io-error:")).as(message).isTrue();
        assertThat(message.contains(KEY)).isFalse();
    }

    @Test
    @DisplayName("리다이렉트를 따라가지 않는다 (API Key가 다른 곳으로 가지 않는다)")
    void redirectIsNotFollowed() throws Exception {
        try (FakeOctomo first = new FakeOctomo(); FakeOctomo elsewhere = new FakeOctomo()) {
            first.status = 302;
            first.location = elsewhere.baseUrl() + "/steal";
            assertThat(unavailable(() -> client(first).messageExists(PHONE, "123456", 5))).isEqualTo("status=302");
            assertThat(elsewhere.calls.get()).isEqualTo(0);
        }
    }

    // ---------------------------------------------------------------- 입력 검증

    @Test
    @DisplayName("형식이 틀린 번호·문구·조회 범위는 OCTOMO를 부르지 않고 거절한다")
    void validatesArguments() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            OctomoHttpClient client = client(fake);
            for (String phone : new String[]{"01112345678", "0101234567", "010123456789", "010-1234-5678", "", null}) {
                assertThat(rejectsArguments(() -> client.messageExists(phone, "123456", 5))).as("번호 " + phone).isTrue();
            }
            for (String text : new String[]{"", "12 34", "<script>", "12\"34", "1".repeat(33), null}) {
                assertThat(rejectsArguments(() -> client.messageExists(PHONE, text, 5))).as("문구 " + text).isTrue();
            }
            assertThat(rejectsArguments(() -> client.messageExists(PHONE, "123456", 0))).as("0분").isTrue();
            assertThat(rejectsArguments(() -> client.messageExists(PHONE, "123456", 61))).as("61분").isTrue();
            assertThat(fake.calls.get()).as("한 번도 호출하지 않았다").isEqualTo(0);
        }
    }

    @Test
    @DisplayName("잘못된 설정(키, 주소, 대표번호, 한도)은 만들 때 거절하고 메시지에 키를 담지 않는다")
    void rejectsBadSettings() {
        Duration t = Duration.ofSeconds(1);
        String secret = "secret-key-should-not-leak";
        String https = "https://api.octoverse.kr/octomo/v1";
        assertThat(rejectsArguments(() -> new OctomoHttpClient("", https, "16663538", t, t, 1, 0))).as("빈 키").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient("short", https, "16663538", t, t, 1, 0))).as("짧은 키").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient("has space in key 1234", https, "16663538", t, t, 1, 0))).as("공백 든 키").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient("line\nbreak-key-12345", https, "16663538", t, t, 1, 0))).as("줄바꿈 든 키").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient(secret, "http://api.octoverse.kr/octomo/v1", "16663538", t, t, 1, 0))).as("http 주소").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient(secret, "https://user:pw@api.octoverse.kr/x", "16663538", t, t, 1, 0))).as("사용자 정보가 든 주소").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient(secret, "ftp://api.octoverse.kr", "16663538", t, t, 1, 0))).as("다른 방식").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient(secret, https, "1666-3538", t, t, 1, 0))).as("대표번호에 하이픈").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient(secret, https, "16663538", t, t, 0, 0))).as("동시 호출 0").isTrue();
        assertThat(rejectsArguments(() -> new OctomoHttpClient(secret, https, "16663538", t, t, 1, -1))).as("음수 한도").isTrue();
        try {
            new OctomoHttpClient(secret, "http://example.com", "16663538", t, t, 1, 0);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains(secret)).as("메시지에 키가 있으면 안 됨").isFalse();
        }
        // 올바른 설정은 만들어진다
        assertThat(new OctomoHttpClient(secret, https, "16663538", t, t, 1, 0).representativeNumber()).isEqualTo("16663538");
    }

    // ---------------------------------------------------------------- 한도와 동시 호출

    @Test
    @DisplayName("하루 호출 한도: 한도를 넘으면 OCTOMO를 부르지 않고 거절한다")
    void dailyLimit() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            OctomoHttpClient client = client(fake, 2, 10, 3000);
            assertThat(client.messageExists(PHONE, "123456", 5)).isTrue();
            assertThat(client.messageExists(PHONE, "123456", 5)).isTrue();
            assertThat(unavailable(() -> client.messageExists(PHONE, "123456", 5))).isEqualTo("daily-call-limit");
            assertThat(fake.calls.get()).as("세 번째는 서버에 가지 않았다").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("하루가 지나면(24시간) 호출 한도가 다시 열린다")
    void dailyLimitResets() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            AtomicLong now = new AtomicLong(1_000_000L);
            OctomoHttpClient client = new OctomoHttpClient(KEY, fake.baseUrl(), "16663538", Duration.ofSeconds(1),
                    Duration.ofSeconds(3), 10, 1, now::get);
            assertThat(client.messageExists(PHONE, "123456", 5)).isTrue();
            assertThat(unavailable(() -> client.messageExists(PHONE, "123456", 5))).isEqualTo("daily-call-limit");
            now.addAndGet(86_399);
            assertThat(unavailable(() -> client.messageExists(PHONE, "123456", 5))).as("23시간 59분 59초 뒤").isEqualTo("daily-call-limit");
            now.addAndGet(1);
            assertThat(client.messageExists(PHONE, "123456", 5)).as("24시간 뒤").isTrue();
        }
    }

    @Test
    @DisplayName("한도를 0으로 두면 하루 호출 수를 제한하지 않는다")
    void zeroMeansUnlimited() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            OctomoHttpClient client = client(fake, 0, 10, 3000);
            for (int i = 0; i < 20; i++) {
                assertThat(client.messageExists(PHONE, "123456", 5)).isTrue();
            }
            assertThat(fake.calls.get()).isEqualTo(20);
        }
    }

    @Test
    @DisplayName("동시 호출 제한: 이미 가득 찼으면 기다리지 않고 바로 거절한다")
    void concurrencyLimit() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            fake.delayMillis = 800;
            OctomoHttpClient client = client(fake, 500, 1, 3000);
            Thread first = new Thread(() -> client.messageExists(PHONE, "123456", 5));
            first.start();
            Thread.sleep(250);   // 첫 호출이 서버에서 처리 중일 때
            long started = System.currentTimeMillis();
            String message = unavailable(() -> client.messageExists(PHONE, "123456", 5));
            long elapsed = System.currentTimeMillis() - started;
            first.join();

            assertThat(message).isEqualTo("too-many-concurrent-calls");
            assertThat(elapsed < 400).as("기다리지 않고 바로 거절 (" + elapsed + "ms)").isTrue();
            assertThat(fake.calls.get()).as("두 번째는 서버에 가지 않았다").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("호출이 끝나면 동시 호출 자리를 반납한다 (오류 응답이어도)")
    void releasesTheSlotAfterFailures() throws Exception {
        try (FakeOctomo fake = new FakeOctomo()) {
            OctomoHttpClient client = client(fake, 500, 1, 3000);
            fake.status = 500;
            for (int i = 0; i < 5; i++) {
                assertThat(unavailable(() -> client.messageExists(PHONE, "123456", 5))).isEqualTo("status=500");
            }
            fake.status = 201;
            assertThat(client.messageExists(PHONE, "123456", 5)).as("자리가 반납돼 있다").isTrue();
        }
    }
}