package com.gamcho.yakyeon.domain.device.logic;

import com.gamcho.yakyeon.common.origin.GatewayOriginPolicy;

import java.util.regex.Pattern;

/**
 * 같은 서버 안의 중계 서버(약통 MQTT 중계)가 백엔드를 부를 때, "믿을 수 있는 호출인지"의 판단 규칙 (스프링·서블릿 없는 순수 로직).
 *
 * 중계 서버에는 약통의 비밀값 원문이 없어서 약통 인증(X-Device-Secret)을 쓸 수 없다. 대신 서버끼리 쓰는 키를 쓰되, 키 하나에 기대지 않고
 * 아래를 모두 만족해야 믿는다.
 *  1) 키가 설정돼 있고 충분히 길다 (설정이 비어 있으면 이 API는 항상 거절한다 - 설정 실수가 "아무나 통과"가 되지 않게).
 *  2) 같은 서버(127.0.0.1, ::1)에서 직접 연결했다. 외부 요청은 API Gateway를 거치므로 접속 주소가 루프백이 될 수 없다.
 *  3) 프록시를 거쳤다는 헤더(X-Forwarded-For, X-Origin-Verify)가 없다. 같은 서버의 프록시가 바깥 요청을 전달해 와도(접속 주소가 루프백으로 보임) 걸러진다.
 *  4) 보낸 키가 맞다. 길이나 앞부분이 맞는 정도가 응답 시간으로 드러나지 않게 해시로 비교한다.
 */
public final class InternalCallerPolicy {

    /** 키 최소 길이. 추측으로 맞힐 수 없게 충분히 길어야 한다 (예: openssl rand -hex 32 = 64자) */
    public static final int MIN_KEY_LENGTH = 32;

    /** 헤더로 오는 키의 최대 길이. 터무니없이 긴 값으로 해시 계산을 시키지 못하게 한다 */
    private static final int MAX_PROVIDED_LENGTH = 256;

    private static final Pattern IPV4_LOOPBACK = Pattern.compile("^127\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private InternalCallerPolicy() {
    }

    /** 설정한 키로 쓸 수 있는지 (비어 있지 않고 최소 길이 이상) */
    public static boolean isUsableKey(String configuredKey) {
        return configuredKey != null && configuredKey.strip().length() >= MIN_KEY_LENGTH;
    }

    public static boolean isTrusted(String configuredKey, String providedKey, String remoteAddr,
                                    boolean hasForwardedForHeader, boolean hasOriginVerifyHeader) {
        if (!isUsableKey(configuredKey)) {
            return false;
        }
        if (hasForwardedForHeader || hasOriginVerifyHeader) {
            return false;
        }
        if (!isLoopback(remoteAddr)) {
            return false;
        }
        if (providedKey == null || providedKey.isBlank() || providedKey.length() > MAX_PROVIDED_LENGTH) {
            return false;
        }
        return GatewayOriginPolicy.secretMatches(configuredKey.strip(), providedKey.strip());
    }

    /** 같은 서버 안에서 온 주소인가. 서블릿이 돌려주는 숫자 주소만 보고 이름 조회는 하지 않는다 */
    static boolean isLoopback(String address) {
        if (address == null) {
            return false;
        }
        if (address.equals("::1") || address.equals("0:0:0:0:0:0:0:1") || address.equals("::ffff:127.0.0.1")) {
            return true;
        }
        var m = IPV4_LOOPBACK.matcher(address);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 3; i++) {
            String part = m.group(i);
            if ((part.length() > 1 && part.charAt(0) == '0') || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }
}