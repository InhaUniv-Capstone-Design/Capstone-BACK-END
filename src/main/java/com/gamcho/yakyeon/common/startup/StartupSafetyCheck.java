package com.gamcho.yakyeon.common.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버가 켜질 때 한 번, 실제 서버에 맞지 않는 설정이면 시작을 거부한다 (규칙은 StartupSafetyRules).
 * 빈이 만들어지는 중에 확인하므로 예약 작업이나 요청 처리가 시작되기 전에 멈춘다.
 * 오류 메시지에는 비밀 값을 담지 않는다.
 */
@Slf4j
@Component
public class StartupSafetyCheck {

    public StartupSafetyCheck(@Value("${spring.datasource.url:}") String datasourceUrl,
                              @Value("${spring.datasource.password:}") String datasourcePassword,
                              @Value("${phone-verification.provider:mock}") String phoneVerificationProvider,
                              @Value("${app.allow-mock-sms:false}") boolean allowMockSms) {
        List<String> problems = StartupSafetyRules.problems(datasourceUrl, datasourcePassword,
                phoneVerificationProvider, allowMockSms);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("서버를 시작할 수 없습니다. 설정을 확인하세요: " + String.join(" / ", problems));
        }
        if (!StartupSafetyRules.isLocal(StartupSafetyRules.databaseHost(datasourceUrl))) {
            log.info("실제 서버 설정 점검을 통과했습니다.");
        }
    }
}