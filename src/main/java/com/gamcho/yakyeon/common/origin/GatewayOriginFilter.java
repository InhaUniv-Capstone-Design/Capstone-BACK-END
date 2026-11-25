package com.gamcho.yakyeon.common.origin;

import com.gamcho.yakyeon.common.origin.GatewayOriginPolicy.Decision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API Gateway를 거치지 않은 요청을 거절하고(켜져 있을 때), 검증된 요청의 진짜 접속 주소를 요청에 붙인다.
 * 판단 규칙은 GatewayOriginPolicy에 있고, 이 필터는 그 결과를 요청과 응답에 적용하기만 한다.
 *
 * 인증 필터보다 먼저 실행해서(가장 앞), 직접 호출은 로그인 검사나 호출 제한까지 가기 전에 막는다.
 * 기본은 꺼져 있어서(gateway-origin.enabled=false) 코드만 배포해서는 동작이 바뀌지 않는다.
 * 켜는 순서는 배포 안내서를 따른다: API Gateway가 먼저 헤더를 붙이게 한 다음에 서버를 켠다(순서가 바뀌면 모든 요청이 403이 된다).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class GatewayOriginFilter extends OncePerRequestFilter {

    /** 검증된 접속 주소를 담는 요청 속성. 호출 제한과 접근 기록이 clientIp()로 읽는다 */
    public static final String CLIENT_IP_ATTRIBUTE = GatewayOriginFilter.class.getName() + ".clientIp";

    private static final long BLOCK_LOG_INTERVAL_MILLIS = 30_000;
    private static final String FORBIDDEN_BODY = "{\"code\":\"FORBIDDEN_ORIGIN\",\"message\":\"허용되지 않은 접근 경로입니다.\"}";

    private final boolean enabled;
    private final String secret;
    private final boolean allowLoopback;
    private final int proxyHops;

    private final AtomicLong lastBlockLogAt = new AtomicLong(0);
    private final AtomicInteger blockedSinceLog = new AtomicInteger();

    @Autowired
    public GatewayOriginFilter(@Value("${gateway-origin.enabled:false}") boolean enabled,
                               @Value("${gateway-origin.secret:}") String secret,
                               @Value("${gateway-origin.allow-loopback:true}") boolean allowLoopback,
                               @Value("${gateway-origin.proxy-hops:1}") int proxyHops) {
        // 서버가 켜질 때 확인한다: 잘못된 설정이면 시작을 거부한다 (오류 메시지에는 비밀 값을 담지 않는다)
        List<String> problems = GatewayOriginPolicy.settingsProblems(enabled, secret, proxyHops);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("서버를 시작할 수 없습니다. 설정을 확인하세요: " + String.join(" / ", problems));
        }
        this.enabled = enabled;
        this.secret = secret == null ? "" : secret.strip();
        this.allowLoopback = allowLoopback;
        this.proxyHops = proxyHops;
        if (enabled) {
            log.info("gateway-origin 켜짐: API Gateway를 거친 요청만 받습니다 (서버 안 점검 요청 허용={}, 프록시 단계={})", allowLoopback, proxyHops);
        } else {
            log.info("gateway-origin 꺼짐: 8080 직접 호출을 막지 않고 접속 주소는 연결된 쪽의 주소를 씁니다");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        Decision decision = GatewayOriginPolicy.decide(true, secret, allowLoopback, proxyHops,
                request.getHeader(GatewayOriginPolicy.ORIGIN_HEADER), request.getRemoteAddr(), forwardedFor(request));
        if (!decision.allowed()) {
            logBlocked(request);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(FORBIDDEN_BODY);
            return;
        }
        request.setAttribute(CLIENT_IP_ATTRIBUTE, decision.clientIp());
        chain.doFilter(request, response);
    }

    /**
     * 호출 제한과 접근 기록이 쓸 접속 주소. 이 필터가 검증한 주소가 있으면 그것을, 없으면(꺼져 있거나 필터를 안 거침) 연결된 쪽의 주소를 돌려준다.
     */
    public static String clientIp(HttpServletRequest request) {
        Object verified = request.getAttribute(CLIENT_IP_ATTRIBUTE);
        if (verified instanceof String ip && !ip.isBlank()) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /** X-Forwarded-For가 여러 줄로 나뉘어 와도 순서대로 이어서 읽는다 (맨 오른쪽 = 가장 나중에 덧붙은 값) */
    private static String forwardedFor(HttpServletRequest request) {
        var headers = request.getHeaders(GatewayOriginPolicy.FORWARDED_FOR_HEADER);
        return headers == null ? null : String.join(",", Collections.list(headers));
    }

    /** 공격이 몰려도 로그가 넘치지 않게 30초에 한 번만 남긴다 (그 사이 거절한 건수를 함께). 비밀 값이나 헤더 내용은 남기지 않는다 */
    private void logBlocked(HttpServletRequest request) {
        blockedSinceLog.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastBlockLogAt.get();
        if (now - last >= BLOCK_LOG_INTERVAL_MILLIS && lastBlockLogAt.compareAndSet(last, now)) {
            String path = request.getRequestURI();
            if (path != null && path.length() > 100) {
                path = path.substring(0, 100);
            }
            log.warn("API Gateway를 거치지 않은 요청을 거절했습니다(최근 {}건): 주소={}, 경로={}",
                    blockedSinceLog.getAndSet(0), request.getRemoteAddr(), path);
        }
    }
}