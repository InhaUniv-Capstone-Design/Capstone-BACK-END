package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.device.logic.InternalCallerPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 같은 서버의 중계 서버가 부르는 API(/device/compartment-state)의 호출자 확인. 판단 규칙은 InternalCallerPolicy에 있다.
 *
 * 설정 device-gateway.key (환경변수 DEVICE_GATEWAY_KEY)는 중계 서버와 같은 값이고, 두 서버의 환경 파일에만 둔다.
 *  - 비어 있으면 이 API는 항상 거절된다 (기본). 키를 넣어야 켜진다.
 *  - 값이 있는데 짧으면(32자 미만) 서버가 시작되지 않는다. 오류 메시지에는 키를 담지 않는다.
 * 실패하면 이유와 상관없이 약통 인증 실패와 똑같은 오류를 준다 (키 없음, 틀린 키, 외부 주소, 프록시 경유를 응답으로 구분할 수 없다).
 */
@Slf4j
@Component
public class GatewayCallerChecker {

    private static final long REJECT_LOG_INTERVAL_MILLIS = 30_000;

    private final String key;
    private final AtomicLong lastRejectLogAt = new AtomicLong(0);
    private final AtomicInteger rejectedSinceLog = new AtomicInteger();

    @Autowired
    public GatewayCallerChecker(@Value("${device-gateway.key:}") String key) {
        String value = key == null ? "" : key.strip();
        if (!value.isEmpty() && !InternalCallerPolicy.isUsableKey(value)) {
            throw new IllegalStateException("서버를 시작할 수 없습니다. 설정을 확인하세요: DEVICE_GATEWAY_KEY는 "
                    + InternalCallerPolicy.MIN_KEY_LENGTH + "자 이상이어야 합니다 (비워 두면 중계 서버 호출 API가 꺼집니다)");
        }
        this.key = value;
        if (value.isEmpty()) {
            log.info("device-gateway.key 없음: 중계 서버 호출 API(/device/compartment-state)는 모든 요청을 거절합니다");
        } else {
            log.info("device-gateway.key 설정됨: 중계 서버 호출 API(/device/compartment-state) 켜짐");
        }
    }

    /** 믿을 수 있는 호출이 아니면 약통 인증 실패와 같은 오류를 던진다 */
    public void requireTrusted(HttpServletRequest request, String providedKey) {
        boolean trusted = InternalCallerPolicy.isTrusted(key, providedKey, request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For") != null, request.getHeader("X-Origin-Verify") != null);
        if (!trusted) {
            logRejected(request);
            throw new BusinessException(ErrorCode.DEVICE_AUTH_FAILED);
        }
    }

    /** 공격이 몰려도 로그가 넘치지 않게 30초에 한 번만 남긴다 (그 사이 거절한 건수와 접속 주소만. 키나 헤더 내용은 남기지 않는다) */
    private void logRejected(HttpServletRequest request) {
        rejectedSinceLog.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastRejectLogAt.get();
        if (now - last >= REJECT_LOG_INTERVAL_MILLIS && lastRejectLogAt.compareAndSet(last, now)) {
            log.warn("중계 서버 호출 API 거절(최근 {}건): 주소={}", rejectedSinceLog.getAndSet(0), request.getRemoteAddr());
        }
    }
}