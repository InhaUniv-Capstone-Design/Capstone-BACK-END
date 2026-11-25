package com.gamcho.yakyeon.client.octomo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 가입 문자 인증을 OCTOMO로 할 때만 클라이언트를 만든다: phone-verification.provider=octomo.
 * 설정이 없거나 mock이면 이 빈이 없고, 서비스는 개발용 방식(인증번호를 서버 로그에 남김)으로 동작한다.
 *
 * API Key는 환경변수 OCTOMO_API_KEY로만 넣는다(저장소에 적지 않는다). 키가 없거나 형식이 이상하면 서버가 시작하지 않는다
 * (조용히 개발용 방식으로 떨어지면 안 되므로). 오류 메시지에는 키를 담지 않는다.
 */
@Configuration
public class OctomoConfig {

    @Bean
    @ConditionalOnProperty(name = "phone-verification.provider", havingValue = "octomo")
    public OctomoClient octomoClient(
            @Value("${octomo.api-key:}") String apiKey,
            @Value("${octomo.base-url:https://api.octoverse.kr/octomo/v1}") String baseUrl,
            @Value("${octomo.representative-number:16663538}") String representativeNumber,
            @Value("${octomo.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${octomo.read-timeout-ms:4000}") long readTimeoutMs,
            @Value("${octomo.max-concurrent-calls:10}") int maxConcurrentCalls,
            @Value("${octomo.daily-call-limit:300}") int dailyCallLimit) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "phone-verification.provider=octomo 로 쓰려면 환경변수 OCTOMO_API_KEY가 필요합니다.");
        }
        return new OctomoHttpClient(apiKey.trim(), baseUrl, representativeNumber,
                Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs), maxConcurrentCalls, dailyCallLimit);
    }
}