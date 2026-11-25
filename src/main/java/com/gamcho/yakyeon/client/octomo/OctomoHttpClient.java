package com.gamcho.yakyeon.client.octomo;

import com.gamcho.yakyeon.common.ratelimit.FixedWindowCounter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCTOMO API 호출 (JDK 내장 HTTP 클라이언트, 스프링 의존 없음).
 *
 * 보안·안정성 원칙
 *  - API Key는 요청 헤더에만 쓰고, 예외 메시지·로그에 절대 남기지 않는다. 키 형식이 이상하면 시작할 때 거절한다.
 *  - 주소는 https만 허용한다(시험용 로컬 서버를 위해 loopback만 http 허용). 리다이렉트는 따라가지 않는다(키가 다른 곳으로 가지 않게).
 *  - 보내는 값은 형식을 검증한 숫자·영문뿐이라 JSON을 직접 조립해도 안전하다. 응답은 필요한 필드만 엄격하게 읽는다.
 *  - 연결·응답 시간 제한, 동시 호출 수 제한, 하루 호출 한도를 둔다(OCTOMO 무료 플랜은 월 10,000건이라 악용으로 소진되는 것을 막는다).
 */
public class OctomoHttpClient implements OctomoClient {

    private static final Pattern API_KEY = Pattern.compile("^[!-~]{8,256}$");
    private static final Pattern MOBILE = Pattern.compile("^010[0-9]{8}$");
    private static final Pattern TEXT = Pattern.compile("^[0-9A-Za-z]{1,32}$");
    private static final Pattern NUMBER = Pattern.compile("^[0-9]{3,15}$");
    private static final Pattern EXISTS = Pattern.compile("\"exists\"\\s*:\\s*(true|false)");

    private static final int MAX_RESPONSE_BYTES = 300_000;
    private static final long DAY_SECONDS = 86_400;

    private final String apiKey;
    private final URI existsUri;
    private final String representativeNumber;
    private final HttpClient http;
    private final Duration readTimeout;
    private final Semaphore concurrency;
    private final int dailyCallLimit;
    private final FixedWindowCounter budget = new FixedWindowCounter(10);
    private final LongSupplier clock;

    public OctomoHttpClient(String apiKey, String baseUrl, String representativeNumber,
                            Duration connectTimeout, Duration readTimeout, int maxConcurrent, int dailyCallLimit) {
        this(apiKey, baseUrl, representativeNumber, connectTimeout, readTimeout, maxConcurrent, dailyCallLimit,
                () -> Instant.now().getEpochSecond());
    }

    OctomoHttpClient(String apiKey, String baseUrl, String representativeNumber,
                     Duration connectTimeout, Duration readTimeout, int maxConcurrent, int dailyCallLimit,
                     LongSupplier clock) {
        if (apiKey == null || !API_KEY.matcher(apiKey).matches()) {
            // 키 값을 메시지에 넣지 않는다
            throw new IllegalArgumentException("OCTOMO API Key 형식이 올바르지 않습니다.");
        }
        if (representativeNumber == null || !NUMBER.matcher(representativeNumber).matches()) {
            throw new IllegalArgumentException("OCTOMO 대표번호는 숫자만 써야 합니다.");
        }
        if (maxConcurrent < 1 || dailyCallLimit < 0 || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("OCTOMO 호출 설정 값이 올바르지 않습니다.");
        }
        URI base = parseBase(baseUrl);
        String root = base.toString().replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.existsUri = URI.create(root + "/public/message/exists");
        this.representativeNumber = representativeNumber;
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

    private static URI parseBase(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OCTOMO 주소가 올바르지 않습니다.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        boolean ok = host != null && uri.getUserInfo() == null
                && ("https".equals(scheme) || ("http".equals(scheme) && loopback));
        if (!ok) {
            throw new IllegalArgumentException("OCTOMO 주소는 https여야 합니다.");
        }
        return uri;
    }

    @Override
    public boolean messageExists(String mobileNum, String text, int withinMinutes) {
        if (mobileNum == null || !MOBILE.matcher(mobileNum).matches()) {
            throw new IllegalArgumentException("휴대폰 번호는 010으로 시작하는 11자리 숫자여야 합니다.");
        }
        requireText(text);
        if (withinMinutes < 1 || withinMinutes > 60) {
            throw new IllegalArgumentException("조회 범위는 1~60분이어야 합니다.");
        }
        String body = post(existsUri, "{\"mobileNum\":\"" + mobileNum + "\",\"text\":\"" + text
                + "\",\"withinMinutes\":" + withinMinutes + "}");
        Matcher m = EXISTS.matcher(body);
        if (!m.find()) {
            throw new OctomoUnavailableException("unexpected-response");
        }
        return Boolean.parseBoolean(m.group(1));
    }

    @Override
    public String representativeNumber() {
        return representativeNumber;
    }

    private static void requireText(String text) {
        if (text == null || !TEXT.matcher(text).matches()) {
            throw new IllegalArgumentException("문자 본문은 영문·숫자 1~32자여야 합니다.");
        }
    }

    private String post(URI uri, String json) {
        if (dailyCallLimit > 0 && !budget.tryAcquire("octomo-daily", dailyCallLimit, DAY_SECONDS, clock.getAsLong())) {
            throw new OctomoUnavailableException("daily-call-limit");
        }
        if (!concurrency.tryAcquire()) {
            throw new OctomoUnavailableException("too-many-concurrent-calls");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(readTimeout)
                    .header("Authorization", "Octomo " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            try (InputStream in = response.body()) {
                if (status < 200 || status >= 300) {
                    throw new OctomoUnavailableException("status=" + status);   // 응답 본문은 읽지도 남기지도 않는다
                }
                byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new OctomoUnavailableException("response-too-large");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new OctomoUnavailableException("io-error:" + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OctomoUnavailableException("interrupted");
        } finally {
            concurrency.release();
        }
    }
}