package com.gamcho.yakyeon.common.origin;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * "API Gateway를 거친 요청만 받고, 그 요청에서 진짜 사용자 접속 주소를 읽는다"의 판단 규칙 (스프링·서블릿 없는 순수 로직).
 *
 * 서버는 API Gateway 뒤에 있어서 접속 주소(getRemoteAddr)가 항상 API Gateway의 주소다. 그러면 (1) 호출 제한이 모든 사용자의 공동 한도가 되고
 * (2) 접근 기록의 IP가 의미가 없으며 (3) API Gateway를 건너뛰고 서버를 직접 부를 수 있다. 이를 막으려고:
 *  - API Gateway가 모든 요청에 비밀 값이 든 헤더(X-Origin-Verify)를 붙이게 하고, 서버는 그 값이 맞는 요청만 받는다 (직접 호출 차단).
 *  - 그렇게 검증된 요청에서만 X-Forwarded-For를 믿는다. 이 헤더는 누구나 만들어 보낼 수 있지만, API Gateway가 진짜 접속 주소를 맨 뒤에
 *    덧붙이므로 "뒤에서 n번째" 값이 진짜 주소다(n = proxy-hops, 보통 1. 앞에 CloudFront 같은 장치가 하나 더 있으면 2).
 *
 * 꺼져 있으면(기본) 아무 것도 바뀌지 않는다: 모든 요청을 받고 접속 주소는 getRemoteAddr 그대로다.
 */
public final class GatewayOriginPolicy {

    public static final String ORIGIN_HEADER = "X-Origin-Verify";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** 비밀 값 최소 길이. 추측으로 맞힐 수 없게 충분히 길어야 한다 (예: openssl rand -hex 32 = 64자) */
    public static final int MIN_SECRET_LENGTH = 32;
    public static final int MAX_PROXY_HOPS = 5;

    public enum Outcome {
        /** 기능이 꺼져 있어서 그대로 통과 */
        DISABLED,
        /** 출처 헤더가 맞아서 통과 (접속 주소는 X-Forwarded-For에서 읽음) */
        VERIFIED,
        /** 같은 서버 안(루프백)에서 온 요청이라 통과 (서버에서 직접 점검할 때) */
        LOOPBACK,
        /** 출처가 확인되지 않아 거절 */
        BLOCKED
    }

    /** @param clientIp 호출 제한과 접근 기록에 쓸 접속 주소 (거절이면 쓰이지 않는다) */
    public record Decision(Outcome outcome, String clientIp) {
        public boolean allowed() {
            return outcome != Outcome.BLOCKED;
        }
    }

    private GatewayOriginPolicy() {
    }

    // ------------------------------------------------------------------ 설정 점검 (서버가 켜질 때)

    /** 서버를 시작하면 안 되는 설정이면 이유 목록을 돌려준다 (비밀 값은 메시지에 담지 않는다) */
    public static List<String> settingsProblems(boolean enabled, String secret, int proxyHops) {
        List<String> problems = new ArrayList<>();
        if (proxyHops < 1 || proxyHops > MAX_PROXY_HOPS) {
            problems.add("gateway-origin.proxy-hops는 1~" + MAX_PROXY_HOPS + " 사이여야 합니다");
        }
        if (enabled && (secret == null || secret.strip().length() < MIN_SECRET_LENGTH)) {
            problems.add("GATEWAY_ORIGIN_ENABLED가 true이면 GATEWAY_ORIGIN_SECRET이 " + MIN_SECRET_LENGTH + "자 이상이어야 합니다");
        }
        return problems;
    }

    // ------------------------------------------------------------------ 요청 하나의 판단

    /**
     * @param originHeader X-Origin-Verify 헤더 값 (없으면 null)
     * @param remoteAddr   서버에 직접 연결한 쪽의 주소 (getRemoteAddr)
     * @param forwardedFor X-Forwarded-For 헤더 전체 (여러 줄이면 쉼표로 이은 것, 없으면 null)
     */
    public static Decision decide(boolean enabled, String secret, boolean allowLoopback, int proxyHops,
                                  String originHeader, String remoteAddr, String forwardedFor) {
        if (!enabled) {
            return new Decision(Outcome.DISABLED, remoteAddr);
        }
        // 켰는데 비밀 값이 비어 있으면 모든 요청을 거절한다 (설정 실수가 "아무나 통과"가 되지 않게. 서버 시작 때도 막지만 한 번 더)
        boolean secretUsable = secret != null && secret.strip().length() >= MIN_SECRET_LENGTH;
        if (secretUsable && originHeader != null && secretMatches(secret.strip(), originHeader.strip())) {
            String client = clientIp(forwardedFor, proxyHops);
            return new Decision(Outcome.VERIFIED, client != null ? client : remoteAddr);
        }
        if (allowLoopback && isLoopback(remoteAddr)) {
            return new Decision(Outcome.LOOPBACK, remoteAddr);
        }
        return new Decision(Outcome.BLOCKED, remoteAddr);
    }

    /** 두 값이 같은지. 길이나 앞부분이 맞는 정도가 응답 시간으로 드러나지 않게 해시로 비교한다 */
    public static boolean secretMatches(String expected, String provided) {
        if (expected == null || expected.isEmpty() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(expected), sha256(provided));
    }

    /**
     * X-Forwarded-For에서 뒤에서 proxyHops번째 값을 접속 주소로 읽는다. 항목이 모자라거나 IP 모양이 아니면 null
     * (호출하는 쪽은 그때 getRemoteAddr로 돌아간다. 값을 일부만 믿거나 고쳐 쓰지 않는다).
     */
    public static String clientIp(String forwardedFor, int proxyHops) {
        if (forwardedFor == null || proxyHops < 1) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        for (String part : forwardedFor.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        if (entries.size() < proxyHops) {
            return null;
        }
        String candidate = entries.get(entries.size() - proxyHops);
        return isIpAddress(candidate) ? candidate : null;
    }

    // ------------------------------------------------------------------ 주소 모양 확인 (DNS 조회를 하지 않는다)

    /** 같은 서버 안에서 온 주소인가 (127.0.0.0/8, IPv6 ::1) */
    static boolean isLoopback(String address) {
        if (address == null || !isIpAddress(address)) {
            return false;
        }
        if (address.indexOf(':') < 0) {
            return address.startsWith("127.");
        }
        try {
            return InetAddress.getByName(address).isLoopbackAddress();   // 숫자 주소라서 이름 조회가 일어나지 않는다
        } catch (Exception e) {
            return false;
        }
    }

    /** IPv4(0~255 네 칸, 앞에 0을 붙이지 않은 형태) 또는 IPv6 숫자 표기. 포트·괄호·이름은 거절한다 */
    static boolean isIpAddress(String s) {
        if (s == null || s.isEmpty() || s.length() > 45) {
            return false;
        }
        return s.indexOf(':') >= 0 ? isIpv6(s) : isIpv4(s);
    }

    private static boolean isIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3 || (p.length() > 1 && p.charAt(0) == '0')) {
                return false;
            }
            for (int i = 0; i < p.length(); i++) {
                if (p.charAt(i) < '0' || p.charAt(i) > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(p) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.';
            if (!ok) {
                return false;   // 이름, 괄호, 포트 구분용 글자, 영역 번호(%)를 모두 거절
            }
        }
        try {
            // ':'가 있는 글은 IPv6 숫자 표기로만 해석되고 실패하면 예외가 나서, 이름 조회(DNS)로 넘어가지 않는다
            InetAddress.getByName(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // 모든 자바가 SHA-256을 갖고 있다
        }
    }
}