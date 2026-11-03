package com.gamcho.yakyeon.common.ratelimit;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * @RateLimit이 붙은 API의 호출 횟수를 접속 주소별로 센다.
 *
 * 접속 주소는 request.getRemoteAddr()다. 서버가 API Gateway 같은 중간 장치 뒤에 있으면 모든 사용자가 그 장치의 주소로
 * 보여서 한도를 함께 쓰게 되니, 그때는 서버 설정(server.forward-headers-strategy와 신뢰할 프록시)으로 실제 사용자 주소가
 * 들어오게 해야 한다. 요청 헤더(X-Forwarded-For)를 이 코드가 직접 믿지는 않는다(누구나 위조할 수 있어서).
 * 한도 설정은 rate-limit.enabled=false로 끌 수 있다(시험용).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_KEYS = 50_000;

    private final FixedWindowCounter counter = new FixedWindowCounter(MAX_KEYS);
    private final boolean enabled;
    private final LongSupplier clock;

    @Autowired
    public RateLimitInterceptor(@Value("${rate-limit.enabled:true}") boolean enabled) {
        this(enabled, () -> Instant.now().getEpochSecond());
    }

    RateLimitInterceptor(boolean enabled, LongSupplier clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit rule = method.getMethodAnnotation(RateLimit.class);
        if (rule == null) {
            return true;
        }
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!counter.tryAcquire(rule.name() + "|" + address, rule.limit(), rule.windowSeconds(), clock.getAsLong())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return true;
    }
}