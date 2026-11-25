package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.domain.medication.ocr.PrescriptionOcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 처방전 인식을 CLOVA OCR로 할 때만 클라이언트를 만든다: ocr.provider=clova (환경변수 OCR_PROVIDER=clova).
 * 개발용 가짜 인식은 ocr.provider=mock 이다(MockPrescriptionOcrClient). 둘 중 하나를 "명시적으로" 정해야 서버가 시작한다.
 *
 * Invoke URL과 Secret Key는 환경변수 CLOVA_OCR_INVOKE_URL, CLOVA_OCR_SECRET_KEY로만 넣는다(저장소에 적지 않는다).
 * 둘 중 하나라도 없거나 형식이 이상하면 서버가 시작하지 않는다(조용히 가짜로 동작하면 안 되므로). 오류 메시지에는 값을 담지 않는다.
 */
@Configuration
public class ClovaOcrConfig {

    private static final Logger log = LoggerFactory.getLogger(ClovaOcrConfig.class);

    @Bean
    @ConditionalOnProperty(name = "ocr.provider", havingValue = "clova")
    public PrescriptionOcrClient clovaPrescriptionOcrClient(
            @Value("${ocr.clova.invoke-url:}") String invokeUrl,
            @Value("${ocr.clova.secret-key:}") String secretKey,
            @Value("${ocr.clova.table-detection:false}") boolean tableDetection,
            @Value("${ocr.clova.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${ocr.clova.read-timeout-ms:20000}") long readTimeoutMs,
            @Value("${ocr.clova.max-concurrent-calls:2}") int maxConcurrentCalls,
            @Value("${ocr.clova.daily-call-limit:100}") int dailyCallLimit) {
        if (invokeUrl == null || invokeUrl.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "ocr.provider=clova 로 쓰려면 환경변수 CLOVA_OCR_INVOKE_URL과 CLOVA_OCR_SECRET_KEY가 필요합니다.");
        }
        // 어느 엔진으로 돌고 있는지 서버 로그만으로 알 수 있게 (키와 주소는 남기지 않는다)
        log.info("처방전 인식 엔진: CLOVA OCR (표 인식={}, 연결 제한 {}ms, 읽기 제한 {}ms, 동시 호출 {}, 하루 한도 {})",
                tableDetection, connectTimeoutMs, readTimeoutMs, maxConcurrentCalls, dailyCallLimit);
        return new ClovaPrescriptionOcrClient(new ClovaOcrHttpClient(invokeUrl.trim(), secretKey.trim(), tableDetection,
                Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs), maxConcurrentCalls, dailyCallLimit));
    }
}