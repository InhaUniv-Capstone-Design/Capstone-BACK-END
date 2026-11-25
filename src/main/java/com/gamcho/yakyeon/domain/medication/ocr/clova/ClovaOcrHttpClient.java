package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.common.ratelimit.FixedWindowCounter;
import com.gamcho.yakyeon.domain.medication.ocr.OcrUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLOVA OCR General API 호출 (JDK 내장 HTTP 클라이언트, 스프링 의존 없음). 요청·응답 형식은 네이버 클라우드 문서(General OCR) 기준이다.
 *
 *   POST {Invoke URL}      (콘솔의 API Gateway 연동에서 복사한 주소. 끝이 /general)
 *   헤더  X-OCR-SECRET: {Secret Key}, Content-Type: application/json
 *   본문  {"version":"V2","requestId":"UUID","timestamp":밀리초,"lang":"ko","images":[{"format":"jpg","name":"..","data":"Base64"}],
 *          "enableTableDetection":false}
 *
 * 보안·안정성 원칙 (OCTOMO 호출부와 같다)
 *  - Secret Key는 요청 헤더에만 쓰고 예외 메시지·로그에 절대 남기지 않는다. 키 형식이 이상하면 시작할 때 거절한다.
 *  - 주소는 https만 허용한다(시험용 로컬 서버를 위해 loopback만 http 허용). 리다이렉트는 따라가지 않는다(키·이미지가 다른 곳으로 가지 않게).
 *  - 처방전 이미지는 요청 본문에만 담고 어디에도 저장하거나 기록하지 않는다. 오류 응답 본문은 4자리 오류 코드만 꺼내 쓴다.
 *  - 연결·응답 시간 제한, 동시 호출 수 제한, 하루 호출 한도를 둔다(무료 제공은 월 100건이고 초과분은 과금이라 반복 호출로 비용이 새는 것을 막는다).
 *    네이버가 권장하는 호출 성능은 계정당 최대 1 tps라서 동시 호출 수는 기본 2로 둔다(ClovaOcrConfig).
 *
 * 네이버 오류 코드(참고): 401/0002 Secret Key 오류, 400/0011 요청 본문 오류, 400/0023 호출 한도 도달, 400/0025 호출 속도 제한,
 * 400/0028 표 추출이 꺼진 도메인에서 enableTableDetection=true로 호출, 500/05xx 서비스 오류.
 */
public class ClovaOcrHttpClient {

    private static final Pattern SECRET = Pattern.compile("^[!-~]{8,256}$");
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"?(\\d{4})");
    private static final int MAX_RESPONSE_BYTES = 8_000_000;
    private static final int MAX_ERROR_BODY_BYTES = 4_000;
    private static final long DAY_SECONDS = 86_400;

    private final String secretKey;
    private final URI invokeUri;
    private final boolean tableDetection;
    private final HttpClient http;
    private final Duration readTimeout;
    private final Semaphore concurrency;
    private final int dailyCallLimit;
    private final FixedWindowCounter budget = new FixedWindowCounter(10);
    private final LongSupplier clock;

    public ClovaOcrHttpClient(String invokeUrl, String secretKey, boolean tableDetection, Duration connectTimeout,
                              Duration readTimeout, int maxConcurrent, int dailyCallLimit) {
        this(invokeUrl, secretKey, tableDetection, connectTimeout, readTimeout, maxConcurrent, dailyCallLimit,
                () -> Instant.now().getEpochSecond());
    }

    ClovaOcrHttpClient(String invokeUrl, String secretKey, boolean tableDetection, Duration connectTimeout,
                       Duration readTimeout, int maxConcurrent, int dailyCallLimit, LongSupplier clock) {
        if (secretKey == null || !SECRET.matcher(secretKey).matches()) {
            // 키 값을 메시지에 넣지 않는다
            throw new IllegalArgumentException("CLOVA OCR Secret Key 형식이 올바르지 않습니다.");
        }
        if (maxConcurrent < 1 || dailyCallLimit < 0 || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("CLOVA OCR 호출 설정 값이 올바르지 않습니다.");
        }
        this.invokeUri = parseInvokeUrl(invokeUrl);
        this.secretKey = secretKey;
        this.tableDetection = tableDetection;
        this.readTimeout = readTimeout;
        this.concurrency = new Semaphore(maxConcurrent);
        this.dailyCallLimit = dailyCallLimit;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private static URI parseInvokeUrl(String invokeUrl) {
        URI uri;
        try {
            uri = URI.create(invokeUrl == null ? "" : invokeUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CLOVA OCR Invoke URL이 올바르지 않습니다.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        boolean ok = host != null && uri.getUserInfo() == null
                && ("https".equals(scheme) || ("http".equals(scheme) && loopback));
        if (!ok) {
            throw new IllegalArgumentException("CLOVA OCR Invoke URL은 https여야 합니다.");
        }
        return uri;
    }

    /**
     * 이미지를 보내 인식 결과(JSON 원문)를 받는다.
     *
     * @param image  JPEG 또는 PNG 이미지의 바이트
     * @param format "jpg" 또는 "png"
     * @throws OcrUnavailableException 호출 한도 초과, 연결 실패, 시간 초과, 오류 응답 등 (원인만 담는다)
     */
    public String recognize(byte[] image, String format) {
        if (image == null || image.length == 0 || (!"jpg".equals(format) && !"png".equals(format))) {
            throw new IllegalArgumentException("이미지 형식은 jpg 또는 png여야 합니다.");
        }
        if (dailyCallLimit > 0 && !budget.tryAcquire("clova-daily", dailyCallLimit, DAY_SECONDS, clock.getAsLong())) {
            throw new OcrUnavailableException("daily-call-limit");
        }
        if (!concurrency.tryAcquire()) {
            throw new OcrUnavailableException("too-many-concurrent-calls");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(invokeUri)
                    .timeout(readTimeout)
                    .header("X-OCR-SECRET", secretKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(image, format), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            try (InputStream in = response.body()) {
                if (status < 200 || status >= 300) {
                    throw new OcrUnavailableException(errorReason(status, in));
                }
                byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new OcrUnavailableException("response-too-large");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new OcrUnavailableException("io-error:" + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrUnavailableException("interrupted");
        } finally {
            concurrency.release();
        }
    }

    /** 보내는 값은 고정 문구, 새로 만든 UUID, 시각, Base64뿐이라 JSON을 직접 조립해도 안전하다 */
    private String requestBody(byte[] image, String format) {
        return "{\"version\":\"V2\",\"requestId\":\"" + UUID.randomUUID() + "\",\"timestamp\":" + System.currentTimeMillis()
                + ",\"lang\":\"ko\",\"images\":[{\"format\":\"" + format + "\",\"name\":\"prescription\",\"data\":\""
                + Base64.getEncoder().encodeToString(image) + "\"}],\"enableTableDetection\":" + tableDetection + "}";
    }

    /** 오류 응답에서는 HTTP 상태와 네이버의 4자리 오류 코드만 꺼낸다 (본문 나머지는 읽어도 남기지 않는다) */
    private static String errorReason(int status, InputStream in) throws IOException {
        String body = new String(in.readNBytes(MAX_ERROR_BODY_BYTES), StandardCharsets.UTF_8);
        Matcher m = ERROR_CODE.matcher(body);
        return m.find() ? "status=" + status + ",code=" + m.group(1) : "status=" + status;
    }
}