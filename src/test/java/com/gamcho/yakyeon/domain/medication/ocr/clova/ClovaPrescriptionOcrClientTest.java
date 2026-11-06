package com.gamcho.yakyeon.domain.medication.ocr.clova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import com.gamcho.yakyeon.domain.medication.ocr.OcrUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 이미지 → (가짜 네이버 서버) → 응답 해석 → 처방전 내용까지 끝에서 끝까지 한 번에 시험한다 */
class ClovaPrescriptionOcrClientTest {

    private static final String KEY = "test-secret-key-1234567890abcdef";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2};

    private static String field(String text, double x, double y) {
        return "{\"valueType\":\"ALL\",\"inferText\":\"" + text + "\",\"inferConfidence\":0.99,\"type\":\"NORMAL\",\"boundingPoly\":{\"vertices\":["
                + "{\"x\":" + x + ",\"y\":" + y + "},{\"x\":" + (x + 12.0 * text.length()) + ",\"y\":" + y + "},{\"x\":"
                + (x + 12.0 * text.length()) + ",\"y\":" + (y + 20) + "},{\"x\":" + x + ",\"y\":" + (y + 20) + "}]}}";
    }

    /** 처방전 한 장을 네이버 V2 응답 모양으로 만든다 (lineBreak 없음: 위치로 묶는 길) */
    private static String prescriptionResponse() {
        String[] fields = {
                field("의료기관명칭", 50, 100), field("서울튼튼병원", 250, 100),
                field("교부연월일", 50, 140), field("2026.11.05", 250, 140),
                field("처방", 50, 200), field("의약품의", 110, 200), field("명칭", 230, 200), field("1회", 420, 200), field("투약량", 470, 200),
                field("1일", 580, 200), field("투여횟수", 620, 200), field("총", 740, 200), field("투약일수", 770, 200), field("용법", 900, 200),
                field("타이레놀정500mg", 50, 240), field("1", 430, 240), field("3", 600, 240), field("3", 760, 240), field("식후", 900, 240), field("30분", 960, 240),
                field("겔포스엠현탁액", 50, 280), field("1", 430, 280), field("3", 600, 280), field("5", 760, 280), field("필요시", 900, 280),
                field("조제약국", 50, 420), field("행복약국", 160, 420)};
        return "{\"version\":\"V2\",\"requestId\":\"x\",\"timestamp\":1,\"images\":[{\"inferResult\":\"SUCCESS\",\"message\":\"SUCCESS\",\"fields\":["
                + String.join(",", fields) + "]}]}";
    }

    private static HttpServer server(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static ClovaPrescriptionOcrClient clientFor(HttpServer server) {
        return new ClovaPrescriptionOcrClient(new ClovaOcrHttpClient("http://127.0.0.1:" + server.getAddress().getPort() + "/general", KEY,
                false, Duration.ofSeconds(3), Duration.ofSeconds(5), 4, 0));
    }

    @Test
    @DisplayName("이미지를 보내면 병원, 약국, 날짜, 약 목록이 나온다 (JPEG와 PNG 모두)")
    void readsPrescriptionEndToEnd() throws Exception {
        HttpServer server = server(200, prescriptionResponse());
        try {
            ClovaPrescriptionOcrClient client = clientFor(server);
            for (byte[] image : new byte[][]{JPEG, PNG}) {
                OcrResult r = client.recognize(image);
                assertThat(r.hospitalName()).isEqualTo("서울튼튼병원");
                assertThat(r.pharmacyName()).isEqualTo("행복약국");
                assertThat(r.prescribedDate()).isEqualTo(LocalDate.of(2026, 11, 5));
                assertThat(r.items().size()).isEqualTo(2);
                assertThat(r.items().get(0).drugName()).isEqualTo("타이레놀정500mg");
                assertThat(r.items().get(0).timesPerDay()).isEqualTo((short) 3);
                assertThat(r.items().get(0).mealRelation().name()).isEqualTo("AFTER_MEAL");
                assertThat(r.items().get(1).prn()).isTrue();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("글자를 못 읽었으면(FAILURE) 약이 없는 결과다. 오류가 아니다")
    void failureMeansEmptyResult() throws Exception {
        HttpServer server = server(200, "{\"images\":[{\"inferResult\":\"FAILURE\",\"message\":\"no text\"}]}");
        try {
            OcrResult r = clientFor(server).recognize(JPEG);
            assertThat(r.items().isEmpty()).isTrue();
            assertThat(r.hospitalName() == null).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("엔진 오류(ERROR), 인증 실패(401), 알 수 없는 응답은 엔진을 쓸 수 없음으로 알린다")
    void unavailableCases() throws Exception {
        Object[][] cases = {
                {200, "{\"images\":[{\"inferResult\":\"ERROR\"}]}", "infer-error"},
                {401, "{\"code\":\"0002\",\"message\":\"Secret key validate failed.\"}", "status=401,code=0002"},
                {500, "{\"code\":\"0501\"}", "status=500,code=0501"},
                {200, "<html>not json</html>", "unexpected-response"}};
        for (Object[] c : cases) {
            HttpServer server = server((Integer) c[0], (String) c[1]);
            try {
                clientFor(server).recognize(JPEG);
                throw new AssertionError("예외가 나야 함: " + c[2]);
            } catch (OcrUnavailableException e) {
                assertThat(e.getReason()).isEqualTo(c[2]);
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    @DisplayName("JPEG/PNG가 아닌 바이트는 네이버에 보내지 않고 거절한다")
    void rejectsNonImage() throws Exception {
        HttpServer server = server(200, prescriptionResponse());
        try {
            clientFor(server).recognize(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
            throw new AssertionError("예외가 나야 함");
        } catch (IllegalArgumentException expected) {
            assertThat(true).isTrue();
        } finally {
            server.stop(0);
        }
    }

    /** 가운데 좌표(cx, cy) 기준 글자 조각을 네이버 V2 응답의 field로 만든다 (실제 처방전 사진의 글자 위치를 옮긴 것) */
    private static String fieldAt(String text, double cx, double cy) {
        return field(text, cx - 6.0 * text.length(), cy - 10);
    }

    /** 아침·점심·저녁 칸이 있는 시험용 처방전(약 2개)을 네이버 응답 모양으로 만든다 */
    private static String slotGridResponse() {
        String[] fields = {
                fieldAt("처방일자", 650, 155), fieldAt(":", 718, 155), fieldAt("2024-05-20", 820, 155),
                fieldAt("의료기관", 650, 331), fieldAt(":", 718, 331), fieldAt("○○○내과의원", 830, 331),
                fieldAt("No.", 68, 586), fieldAt("약품명", 250, 586), fieldAt("용량/規格", 484, 586), fieldAt("투약일수", 640, 586),
                fieldAt("복용법", 840, 557), fieldAt("아침", 760, 617), fieldAt("점심", 875, 617), fieldAt("저녁", 991, 617),
                fieldAt("1", 68, 712), fieldAt("아모디핀정", 215, 693), fieldAt("5mg", 320, 693), fieldAt("(Amlodipine)", 252, 732),
                fieldAt("1정", 480, 712), fieldAt("30일", 639, 712), fieldAt("1정", 760, 712), fieldAt("-", 875, 712), fieldAt("1정", 991, 712),
                fieldAt("2", 68, 843), fieldAt("메토프로민정", 190, 825), fieldAt("500mg", 340, 825), fieldAt("(Metformin)", 252, 863),
                fieldAt("1정", 480, 843), fieldAt("30일", 639, 843), fieldAt("1정", 760, 843), fieldAt("1정", 875, 843), fieldAt("1정", 991, 843),
                fieldAt("※", 52, 1000), fieldAt("복약", 90, 1000), fieldAt("지도", 135, 1000)};
        return "{\"version\":\"V2\",\"images\":[{\"inferResult\":\"SUCCESS\",\"fields\":[" + String.join(",", fields) + "]}]}";
    }

    @Test
    @DisplayName("아침·점심·저녁 칸이 있는 처방전도 끝까지 읽는다: 시간대, 하루 횟수, 투약일수, 영문 병기 제거")
    void readsTimeSlotPrescriptionEndToEnd() throws Exception {
        HttpServer server = server(200, slotGridResponse());
        try {
            OcrResult r = clientFor(server).recognize(JPEG);
            assertThat(r.hospitalName().endsWith("내과의원")).isTrue();
            assertThat(r.prescribedDate()).isEqualTo(LocalDate.of(2024, 5, 20));
            assertThat(r.items().size()).isEqualTo(2);
            assertThat(r.items().get(0).drugName()).isEqualTo("아모디핀정 5mg");
            assertThat(r.items().get(0).timingSlots().toString()).isEqualTo("[MORNING, DINNER]");
            assertThat(r.items().get(0).timesPerDay()).isEqualTo((short) 2);
            assertThat(r.items().get(0).totalDays()).isEqualTo(30);
            assertThat(r.items().get(1).drugName()).isEqualTo("메토프로민정 500mg");
            assertThat(r.items().get(1).timingSlots().toString()).isEqualTo("[MORNING, LUNCH, DINNER]");
        } finally {
            server.stop(0);
        }
    }
}