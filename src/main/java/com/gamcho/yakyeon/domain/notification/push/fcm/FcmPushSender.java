package com.gamcho.yakyeon.domain.notification.push.fcm;

import com.gamcho.yakyeon.domain.notification.push.PushMessage;
import com.gamcho.yakyeon.domain.notification.push.PushResult;
import com.gamcho.yakyeon.domain.notification.push.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 실제 FCM으로 푸시를 보낸다 (FCM HTTP v1: POST /v1/projects/{프로젝트}/messages:send). MockPushSender 자리에 들어가는 구현이다.
 *
 * PushSender의 약속대로 예외를 던지지 않고 PushResult로 돌려준다.
 *  - 토큰이 무효(UNREGISTERED)면 INVALID_TOKEN → 발송 기록 쪽이 그 토큰을 지운다.
 *  - 그 밖의 실패(네트워크, 인증, 한도 초과, 서버 오류, 설정 오류)는 TRANSIENT_FAILURE → 1·5·15·60분 뒤 다시 보내고 5번째 실패에서 포기한다.
 *  - 액세스 토큰이 만료돼 401이면 새로 받아서 한 번만 다시 보낸다(무한 반복하지 않는다).
 *
 * 로그에는 HTTP 상태와 오류 이름만 남긴다. 기기 토큰, 알림 문구, 액세스 토큰, 키는 남기지 않는다.
 */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final int MAX_RESPONSE_BYTES = 64_000;

    private final GoogleAccessTokenProvider tokens;
    private final URI sendUri;
    private final int ttlSeconds;
    private final Duration readTimeout;
    private final HttpClient http;

    public FcmPushSender(GoogleAccessTokenProvider tokens, URI sendUri, int ttlSeconds, Duration connectTimeout, Duration readTimeout) {
        this.tokens = tokens;
        this.sendUri = sendUri;
        this.ttlSeconds = ttlSeconds;
        this.readTimeout = readTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).build();
    }

    /** 프로젝트 번호로 만든 FCM 발송 주소 */
    public static URI sendUri(String baseUrl, String projectId) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + "/v1/projects/" + projectId + "/messages:send");
    }

    @Override
    public PushResult send(PushMessage message) {
        try {
            String body = FcmJson.message(message, ttlSeconds);
            Reply response = post(body, tokens.accessToken());
            if (response.statusCode() == 401) {
                tokens.invalidate();
                response = post(body, tokens.accessToken());
            }
            PushResult result = FcmErrorClassifier.classify(response.statusCode(), response.body());
            if (result.status() == PushResult.Status.TRANSIENT_FAILURE) {
                log.warn("FCM 발송 일시 실패: status={}, code={}", response.statusCode(), result.errorCode());
            }
            return result;
        } catch (FcmAuthException e) {
            log.error("FCM 인증 실패: {}", e.reason());
            return PushResult.transientFailure("AUTH_FAILED");
        } catch (IOException e) {
            log.warn("FCM 연결 실패: {}", e.getClass().getSimpleName());
            return PushResult.transientFailure("NETWORK");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushResult.transientFailure("INTERRUPTED");
        } catch (RuntimeException e) {
            log.error("FCM 발송 처리 오류: {}", e.getClass().getSimpleName());
            return PushResult.transientFailure("UNEXPECTED");
        }
    }

    private Reply post(String body, String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(sendUri).timeout(readTimeout)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<InputStream> raw = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = raw.body()) {
            String text = new String(in.readNBytes(MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
            return new Reply(raw.statusCode(), text);
        }
    }

    /** FCM이 돌려준 HTTP 상태와 본문 (스트림을 닫은 뒤에도 쓰려고 문자열로 담는다) */
    private record Reply(int statusCode, String body) {
    }
}