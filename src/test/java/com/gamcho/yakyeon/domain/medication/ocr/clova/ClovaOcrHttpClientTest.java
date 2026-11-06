package com.gamcho.yakyeon.domain.medication.ocr.clova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.OcrUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가짜 CLOVA OCR 서버를 이 컴퓨터에 실제로 띄워서 HTTP로 시험한다(외부 연결 없음, Secret Key 필요 없음).
 * 요청의 모양(헤더, 본문), 오류 응답, 시간 초과, 호출 한도, 동시 호출 제한, 리다이렉트, 설정 검증을 확인한다.
 */
class ClovaOcrHttpClientTest {

    private static final String KEY = "test-secret-key-1234567890abcdef";
    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};
    private static final String PATH = "/custom/v1/12345/abcdef/general";

    private static final class FakeClova implements AutoCloseable {
        final HttpServer server;
        volatile int status = 200;
        volatile String body = "{\"images\":[{\"inferResult\":\"SUCCESS\",\"fields\":[]}]}";
        volatile long delayMillis = 0;
        volatile String location = null;
        final AtomicInteger calls = new AtomicInteger();
        final List<String> methods = Collections.synchronizedList(new ArrayList<>());
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
        final List<String> secrets = Collections.synchronizedList(new ArrayList<>());
        final List<String> contentTypes = Collections.synchronizedList(new ArrayList<>());
        final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

        FakeClova() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                methods.add(exchange.getRequestMethod());
                paths.add(exchange.getRequestURI().getPath());
                secrets.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-OCR-SECRET")));
                contentTypes.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
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

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static ClovaOcrHttpClient client(FakeClova fake, boolean table, long readMillis, int concurrent, int dailyLimit) {
        return new ClovaOcrHttpClient(fake.url(), KEY, table, Duration.ofSeconds(3), Duration.ofMillis(readMillis), concurrent, dailyLimit);
    }

    private static String reasonOf(ClovaOcrHttpClient client) {
        try {
            client.recognize(IMAGE, "jpg");
        } catch (OcrUnavailableException e) {
            return e.getReason();
        }
        return null;
    }

    @Test
    @DisplayName("네이버 문서대로 보낸다: POST, X-OCR-SECRET, JSON 본문(version V2, lang ko, Base64 이미지)")
    void sendsDocumentedRequest() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            String result = client(fake, false, 5000, 4, 100).recognize(IMAGE, "jpg");
            assertThat(result).isEqualTo(fake.body);
            assertThat(fake.calls.get()).isEqualTo(1);
            assertThat(fake.methods.get(0)).isEqualTo("POST");
            assertThat(fake.paths.get(0)).isEqualTo(PATH);
            assertThat(fake.secrets.get(0)).isEqualTo(KEY);
            assertThat(fake.contentTypes.get(0)).isEqualTo("application/json");
            String body = fake.bodies.get(0);
            assertThat(body.contains("\"version\":\"V2\"")).isTrue();
            assertThat(body.contains("\"lang\":\"ko\"")).isTrue();
            assertThat(body.contains("\"format\":\"jpg\"")).isTrue();
            assertThat(body.contains("\"name\":\"prescription\"")).isTrue();
            assertThat(body.contains("\"data\":\"" + Base64.getEncoder().encodeToString(IMAGE) + "\"")).as("이미지가 Base64로 들어감").isTrue();
            assertThat(body.contains("\"enableTableDetection\":false")).isTrue();
            assertThat(body.matches(".*\"requestId\":\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\".*")).as("요청 UUID").isTrue();
            assertThat(body.matches(".*\"timestamp\":[0-9]{13}[,}].*")).as("밀리초 시각").isTrue();
        }
    }

    @Test
    @DisplayName("PNG는 format png, 표 인식을 켜면 enableTableDetection이 true다")
    void pngAndTableDetection() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            client(fake, true, 5000, 4, 100).recognize(IMAGE, "png");
            assertThat(fake.bodies.get(0).contains("\"format\":\"png\"")).isTrue();
            assertThat(fake.bodies.get(0).contains("\"enableTableDetection\":true")).isTrue();
        }
    }

    @Test
    @DisplayName("오류 응답은 상태와 네이버 오류 코드(4자리)만 원인으로 남긴다")
    void errorResponses() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            ClovaOcrHttpClient client = client(fake, false, 5000, 4, 0);
            fake.status = 401;
            fake.body = "{\"code\":\"0002\",\"message\":\"Secret key validate failed.\"}";
            assertThat(reasonOf(client)).isEqualTo("status=401,code=0002");
            fake.status = 400;
            fake.body = "{\"code\":\"0028\",\"message\":\"Table detection disabled\"}";
            assertThat(reasonOf(client)).isEqualTo("status=400,code=0028");
            fake.status = 400;
            fake.body = "{\"code\":0023}";
            assertThat(reasonOf(client)).as("숫자로 와도 읽음").isEqualTo("status=400,code=0023");
            fake.status = 500;
            fake.body = "internal error";
            assertThat(reasonOf(client)).isEqualTo("status=500");
            fake.status = 503;
            fake.body = "{\"code\":\"abc\"}";
            assertThat(reasonOf(client)).as("4자리 숫자가 아니면 코드 없음").isEqualTo("status=503");
        }
    }

    @Test
    @DisplayName("응답이 늦으면 시간 초과로 끝낸다")
    void readTimeout() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            fake.delayMillis = 1500;
            String reason = reasonOf(client(fake, false, 300, 4, 100));
            assertThat(reason != null && reason.startsWith("io-error")).as("원인: " + reason).isTrue();
        }
    }

    @Test
    @DisplayName("서버에 연결할 수 없으면 오류다")
    void connectionRefused() throws Exception {
        String url;
        try (FakeClova fake = new FakeClova()) {
            url = fake.url();
        }
        ClovaOcrHttpClient client = new ClovaOcrHttpClient(url, KEY, false, Duration.ofSeconds(1), Duration.ofSeconds(1), 4, 100);
        String reason = reasonOf(client);
        assertThat(reason != null && reason.startsWith("io-error")).as("원인: " + reason).isTrue();
    }

    @Test
    @DisplayName("하루 호출 한도: 넘으면 네이버에 보내지 않고 거절, 다음 날에는 다시 쓸 수 있다")
    void dailyLimit() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            AtomicReference<Long> now = new AtomicReference<>(1_000_000L);
            ClovaOcrHttpClient client = new ClovaOcrHttpClient(fake.url(), KEY, false, Duration.ofSeconds(3), Duration.ofSeconds(5), 4, 2,
                    now::get);
            client.recognize(IMAGE, "jpg");
            client.recognize(IMAGE, "jpg");
            assertThat(reasonOf(client)).isEqualTo("daily-call-limit");
            assertThat(fake.calls.get()).as("한도를 넘은 호출은 네이버로 가지 않는다").isEqualTo(2);
            now.set(1_000_000L + 86_400L + 1);
            client.recognize(IMAGE, "jpg");
            assertThat(fake.calls.get()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("한도를 0으로 하면 하루 한도를 두지 않는다")
    void zeroMeansNoDailyLimit() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            ClovaOcrHttpClient client = client(fake, false, 5000, 4, 0);
            for (int i = 0; i < 6; i++) {
                client.recognize(IMAGE, "jpg");
            }
            assertThat(fake.calls.get()).isEqualTo(6);
        }
    }

    @Test
    @DisplayName("동시 호출 수를 넘으면 기다리지 않고 거절한다")
    void concurrencyLimit() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            fake.delayMillis = 800;
            ClovaOcrHttpClient client = client(fake, false, 5000, 1, 100);
            Thread first = new Thread(() -> client.recognize(IMAGE, "jpg"));
            first.start();
            Thread.sleep(250);
            assertThat(reasonOf(client)).isEqualTo("too-many-concurrent-calls");
            first.join();
            client.recognize(IMAGE, "jpg");
            assertThat(fake.calls.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("리다이렉트는 따라가지 않는다 (키와 이미지가 다른 곳으로 가지 않게)")
    void redirectsAreNotFollowed() throws Exception {
        try (FakeClova other = new FakeClova(); FakeClova fake = new FakeClova()) {
            fake.status = 302;
            fake.location = other.url();
            assertThat(reasonOf(client(fake, false, 5000, 4, 100))).isEqualTo("status=302");
            assertThat(other.calls.get()).as("다른 서버로 가지 않음").isEqualTo(0);
        }
    }

    @Test
    @DisplayName("너무 큰 응답은 거절한다")
    void tooLargeResponse() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            fake.body = "x".repeat(8_100_000);
            assertThat(reasonOf(client(fake, false, 20000, 4, 0))).isEqualTo("response-too-large");
        }
    }

    @Test
    @DisplayName("Secret Key는 어떤 실패에서도 원인에 들어가지 않는다")
    void secretNeverLeaks() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            ClovaOcrHttpClient client = client(fake, false, 400, 4, 0);
            fake.status = 401;
            fake.body = "{\"code\":\"0002\",\"message\":\"" + KEY + "\"}";
            assertThat(reasonOf(client).contains(KEY)).isFalse();
            fake.status = 200;
            fake.delayMillis = 1200;
            assertThat(reasonOf(client).contains(KEY)).isFalse();
        }
        for (String badKey : new String[]{"short", "has space inside the key value", "한글키한글키한글키한글키", ""}) {
            try {
                new ClovaOcrHttpClient("https://example.apigw.ntruss.com/custom/v1/1/a/general", badKey, false,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1);
                throw new AssertionError("거절해야 함: " + badKey);
            } catch (IllegalArgumentException e) {
                assertThat(badKey.isEmpty() || !e.getMessage().contains(badKey)).as("메시지에 키가 들어감").isTrue();
            }
        }
    }

    @Test
    @DisplayName("주소는 https만(loopback 시험 서버는 http 허용), 사용자 정보가 있거나 주소가 이상하면 거절한다")
    void urlValidation() {
        String[] bad = {"http://example.apigw.ntruss.com/x", "ftp://example.com/x", "https://user:pw@example.apigw.ntruss.com/x",
                "example.apigw.ntruss.com/x", "", "https://", "http://localhost.evil.com/x"};
        for (String url : bad) {
            try {
                new ClovaOcrHttpClient(url, KEY, false, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1);
                throw new AssertionError("거절해야 함: " + url);
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage().contains(KEY)).isFalse();
            }
        }
        new ClovaOcrHttpClient("https://abc.apigw.ntruss.com/custom/v1/1/a/general", KEY, false, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1);
        new ClovaOcrHttpClient("http://127.0.0.1:8080/x", KEY, false, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1);
    }

    @Test
    @DisplayName("설정 값이 이상하면 거절한다 (동시 호출 0, 시간 0 이하, 음수 한도)")
    void settingValidation() {
        Object[][] bad = {{0, 1, 1000L}, {1, -1, 1000L}, {1, 1, 0L}, {1, 1, -5L}};
        for (Object[] b : bad) {
            try {
                new ClovaOcrHttpClient("https://abc.apigw.ntruss.com/x", KEY, false, Duration.ofMillis((Long) b[2]),
                        Duration.ofSeconds(1), (Integer) b[0], (Integer) b[1]);
                throw new AssertionError("거절해야 함");
            } catch (IllegalArgumentException expected) {
                assertThat(true).isTrue();
            }
        }
    }

    @Test
    @DisplayName("이미지가 없거나 형식이 jpg/png가 아니면 거절한다")
    void imageArguments() throws Exception {
        try (FakeClova fake = new FakeClova()) {
            ClovaOcrHttpClient client = client(fake, false, 5000, 4, 100);
            int rejected = 0;
            for (Object[] c : new Object[][]{{null, "jpg"}, {new byte[0], "jpg"}, {IMAGE, "gif"}, {IMAGE, null}, {IMAGE, "JPG"}}) {
                try {
                    client.recognize((byte[]) c[0], (String) c[1]);
                } catch (IllegalArgumentException e) {
                    rejected++;
                }
            }
            assertThat(rejected).isEqualTo(5);
            assertThat(fake.calls.get()).as("거절된 요청은 네이버로 가지 않음").isEqualTo(0);
        }
    }
}