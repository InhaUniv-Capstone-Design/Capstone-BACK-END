package com.gamcho.yakyeon.domain.notification.push.fcm;

import com.gamcho.yakyeon.domain.notification.push.PushSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 푸시를 실제 FCM으로 보낼 때만 발송기를 만든다: push.provider=fcm (환경변수 PUSH_PROVIDER=fcm).
 * 개발용 가짜 발송은 push.provider=mock 이다(MockPushSender). 둘 중 하나를 "명시적으로" 정해야 서버가 시작한다.
 *
 * 서비스 계정 키(Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성)는 둘 중 한 방법으로 준다.
 *  - 파일 경로: push.fcm.service-account-file (환경변수 FCM_SERVICE_ACCOUNT_FILE). 서버에서는 이 방법을 권한다(파일 권한 600).
 *  - JSON 내용: push.fcm.service-account-json (환경변수 FCM_SERVICE_ACCOUNT_JSON). 파일을 둘 수 없는 환경에서만.
 * 저장소에는 절대 넣지 않는다. 키가 없거나 형식이 이상하면 서버가 시작하지 않는다(조용히 푸시가 안 나가면 안 되므로).
 * 오류 메시지에는 키 내용을 담지 않는다.
 */
@Configuration
public class FcmConfig {

    private static final String FCM_BASE_URL = "https://fcm.googleapis.com";

    @Bean
    @ConditionalOnProperty(name = "push.provider", havingValue = "fcm")
    public PushSender fcmPushSender(
            @Value("${push.fcm.project-id:}") String projectIdSetting,
            @Value("${push.fcm.service-account-file:}") String keyFile,
            @Value("${push.fcm.service-account-json:}") String keyJson,
            @Value("${push.fcm.ttl-seconds:3600}") int ttlSeconds,
            @Value("${push.fcm.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${push.fcm.read-timeout-ms:10000}") long readTimeoutMs) {
        ServiceAccountKey key = ServiceAccountKey.parse(loadKey(keyFile, keyJson));
        String projectId = projectIdSetting == null || projectIdSetting.isBlank() ? key.projectId() : projectIdSetting.trim();
        if (!projectId.matches("[a-z][a-z0-9-]{4,29}")) {
            throw new IllegalStateException("Firebase 프로젝트 번호를 알 수 없습니다. FCM_PROJECT_ID를 설정하거나 서비스 계정 키의 project_id를 확인하세요.");
        }
        // FCM이 받아 주는 보관 시간은 0초부터 4주까지다
        int ttl = Math.max(60, Math.min(2_419_200, ttlSeconds));
        Duration connect = Duration.ofMillis(connectTimeoutMs);
        Duration read = Duration.ofMillis(readTimeoutMs);
        return new FcmPushSender(new GoogleAccessTokenProvider(key, connect, read),
                FcmPushSender.sendUri(FCM_BASE_URL, projectId), ttl, connect, read);
    }

    private static String loadKey(String file, String json) {
        if (file != null && !file.isBlank()) {
            try {
                return Files.readString(Path.of(file.trim()), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("FCM_SERVICE_ACCOUNT_FILE 경로의 서비스 계정 키 파일을 읽을 수 없습니다.");
            }
        }
        if (json != null && !json.isBlank()) {
            return json;
        }
        throw new IllegalStateException("push.provider=fcm 으로 쓰려면 FCM_SERVICE_ACCOUNT_FILE(키 파일 경로) 또는 FCM_SERVICE_ACCOUNT_JSON이 필요합니다.");
    }
}