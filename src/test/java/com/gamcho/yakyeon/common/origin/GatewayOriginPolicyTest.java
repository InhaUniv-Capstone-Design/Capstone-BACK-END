package com.gamcho.yakyeon.common.origin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.common.origin.GatewayOriginPolicy.Decision;
import com.gamcho.yakyeon.common.origin.GatewayOriginPolicy.Outcome;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatewayOriginPolicyTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";   // 64자
    private static final String GATEWAY = "10.0.0.5";   // API Gateway가 서버에 연결하는 주소

    private static Decision decide(boolean enabled, boolean allowLoopback, int hops, String header, String remote, String xff) {
        return GatewayOriginPolicy.decide(enabled, SECRET, allowLoopback, hops, header, remote, xff);
    }

    // ------------------------------------------------------------------ 꺼져 있을 때 (기본)

    @Test
    @DisplayName("꺼져 있으면 헤더가 없어도 모두 통과하고 접속 주소는 getRemoteAddr 그대로다 (지금 동작과 같다)")
    void disabledChangesNothing() {
        Decision d = decide(false, true, 1, null, GATEWAY, "1.2.3.4");
        assertThat(d.outcome()).isEqualTo(Outcome.DISABLED);
        assertThat(d.allowed()).isTrue();
        assertThat(d.clientIp()).as("X-Forwarded-For를 믿지 않는다").isEqualTo(GATEWAY);
    }

    // ------------------------------------------------------------------ 켜져 있을 때

    @Test
    @DisplayName("올바른 출처 헤더가 있으면 통과하고, 접속 주소는 X-Forwarded-For의 맨 오른쪽 값이다")
    void verifiedRequestUsesTheRightmostForwardedAddress() {
        Decision d = decide(true, true, 1, SECRET, GATEWAY, "6.6.6.6, 203.0.113.7");
        assertThat(d.outcome()).isEqualTo(Outcome.VERIFIED);
        assertThat(d.clientIp()).as("앞의 6.6.6.6은 사용자가 위조해 넣은 값").isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("출처 헤더가 없으면 거절한다 (API Gateway를 건너뛴 직접 호출)")
    void missingHeaderIsBlocked() {
        assertThat(decide(true, true, 1, null, "198.51.100.9", "203.0.113.7").outcome()).isEqualTo(Outcome.BLOCKED);
    }

    @Test
    @DisplayName("출처 헤더가 틀리면 거절한다 (비슷한 값, 앞부분만 맞는 값, 빈 값 포함)")
    void wrongHeaderIsBlocked() {
        assertThat(decide(true, true, 1, SECRET.substring(0, 63) + "0", "198.51.100.9", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, SECRET.substring(0, 40), "198.51.100.9", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, "", "198.51.100.9", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, SECRET + "x", "198.51.100.9", null).outcome()).isEqualTo(Outcome.BLOCKED);
    }

    @Test
    @DisplayName("헤더가 틀린 요청이 X-Forwarded-For를 위조해도 거절한다 (위조 주소로 한도를 피하거나 기록을 속일 수 없다)")
    void forgedForwardedForDoesNotHelpWithoutTheSecret() {
        Decision d = decide(true, true, 1, "wrong-secret", "198.51.100.9", "203.0.113.7");
        assertThat(d.outcome()).isEqualTo(Outcome.BLOCKED);
    }

    @Test
    @DisplayName("켰는데 비밀 값이 비어 있거나 짧으면 모든 요청을 거절한다 (설정 실수가 '아무나 통과'가 되지 않게)")
    void unusableSecretBlocksEverything() {
        assertThat(GatewayOriginPolicy.decide(true, "", false, 1, "", "198.51.100.9", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(GatewayOriginPolicy.decide(true, null, false, 1, null, "198.51.100.9", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(GatewayOriginPolicy.decide(true, "short", false, 1, "short", "198.51.100.9", null).outcome()).as("짧은 비밀 값은 같아도 인정 안 함").isEqualTo(Outcome.BLOCKED);
    }

    // ------------------------------------------------------------------ 같은 서버 안의 점검 요청

    @Test
    @DisplayName("같은 서버 안(127.0.0.1, ::1)에서 온 요청은 헤더 없이도 통과한다 (서버에서 직접 점검용). 접속 주소는 그 주소")
    void loopbackIsAllowed() {
        Decision v4 = decide(true, true, 1, null, "127.0.0.1", "203.0.113.7");
        assertThat(v4.outcome()).isEqualTo(Outcome.LOOPBACK);
        assertThat(v4.clientIp()).as("검증되지 않은 X-Forwarded-For는 믿지 않는다").isEqualTo("127.0.0.1");
        assertThat(decide(true, true, 1, null, "0:0:0:0:0:0:0:1", null).outcome()).isEqualTo(Outcome.LOOPBACK);
        assertThat(decide(true, true, 1, null, "::1", null).outcome()).isEqualTo(Outcome.LOOPBACK);
    }

    @Test
    @DisplayName("루프백 허용을 끄면 같은 서버에서 온 요청도 헤더가 필요하다 (서버 앞에 같은 서버의 프록시가 있을 때)")
    void loopbackCanBeDisabled() {
        assertThat(decide(true, false, 1, null, "127.0.0.1", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, false, 1, SECRET, "127.0.0.1", "203.0.113.7").outcome()).isEqualTo(Outcome.VERIFIED);
    }

    @Test
    @DisplayName("루프백이 아닌 사설 주소나 127로 시작하는 이름은 루프백으로 보지 않는다")
    void onlyRealLoopbackCounts() {
        assertThat(decide(true, true, 1, null, "10.0.0.5", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, null, "192.168.0.1", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, null, "localhost", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, null, "127.evil.com", null).outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(decide(true, true, 1, null, null, null).outcome()).isEqualTo(Outcome.BLOCKED);
    }

    // ------------------------------------------------------------------ 접속 주소 읽기

    @Test
    @DisplayName("proxy-hops=1이면 맨 오른쪽, 2이면 오른쪽에서 두 번째 값 (앞에 CloudFront 같은 장치가 하나 더 있을 때)")
    void hopsSelectTheRightEntry() {
        String xff = "6.6.6.6, 203.0.113.7, 130.176.0.1";
        assertThat(GatewayOriginPolicy.clientIp(xff, 1)).isEqualTo("130.176.0.1");
        assertThat(GatewayOriginPolicy.clientIp(xff, 2)).isEqualTo("203.0.113.7");
        assertThat(GatewayOriginPolicy.clientIp(xff, 3)).isEqualTo("6.6.6.6");
    }

    @Test
    @DisplayName("값이 하나뿐이어도(사용자가 아무것도 안 보낸 경우) 그 값을 쓰고, 공백과 빈 칸은 건너뛴다")
    void singleEntryAndWhitespace() {
        assertThat(GatewayOriginPolicy.clientIp("203.0.113.7", 1)).isEqualTo("203.0.113.7");
        assertThat(GatewayOriginPolicy.clientIp("  6.6.6.6 ,, 203.0.113.7  ", 1)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("항목이 모자라거나 헤더가 없거나 IP 모양이 아니면 null (getRemoteAddr로 돌아간다)")
    void untrustworthyForwardedForFallsBack() {
        assertThat(GatewayOriginPolicy.clientIp("203.0.113.7", 2) == null).isTrue();
        assertThat(GatewayOriginPolicy.clientIp(null, 1) == null).isTrue();
        assertThat(GatewayOriginPolicy.clientIp("", 1) == null).isTrue();
        assertThat(GatewayOriginPolicy.clientIp("not-an-ip", 1) == null).isTrue();
        assertThat(GatewayOriginPolicy.clientIp("203.0.113.7:8080", 1) == null).as("포트가 붙은 값").isTrue();
        assertThat(GatewayOriginPolicy.clientIp("203.0.113.7, evil.example.com", 1) == null).as("맨 오른쪽이 이름이면 앞의 값을 대신 쓰지 않는다").isTrue();
        assertThat(GatewayOriginPolicy.clientIp("1.2.3.4", 0) == null).isTrue();
    }

    @Test
    @DisplayName("검증된 요청이어도 X-Forwarded-For가 이상하면 접속 주소는 getRemoteAddr다")
    void verifiedButMalformedForwardedForUsesRemoteAddr() {
        Decision d = decide(true, true, 1, SECRET, GATEWAY, "garbage");
        assertThat(d.outcome()).isEqualTo(Outcome.VERIFIED);
        assertThat(d.clientIp()).isEqualTo(GATEWAY);
    }

    @Test
    @DisplayName("IPv6 주소도 읽는다 (SQL의 inet이 받을 수 있는 모양만)")
    void ipv6Addresses() {
        assertThat(GatewayOriginPolicy.clientIp("2001:db8::1", 1)).isEqualTo("2001:db8::1");
        assertThat(GatewayOriginPolicy.clientIp("6.6.6.6, 2001:0db8:85a3:0000:0000:8a2e:0370:7334", 1)).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertThat(GatewayOriginPolicy.clientIp("[2001:db8::1]", 1) == null).as("대괄호").isTrue();
        assertThat(GatewayOriginPolicy.clientIp("2001:db8::1%eth0", 1) == null).as("영역 번호").isTrue();
        assertThat(GatewayOriginPolicy.clientIp("2001:db8::zz", 1) == null).isTrue();
        assertThat(GatewayOriginPolicy.clientIp("1:2:3:4:5:6:7:8:9", 1) == null).as("칸이 너무 많음").isTrue();
    }

    @Test
    @DisplayName("IPv4는 네 칸, 0~255, 앞에 0을 붙이지 않은 형태만 받는다")
    void strictIpv4() {
        for (String ok : List.of("0.0.0.0", "255.255.255.255", "1.2.3.4", "10.0.0.5")) {
            assertThat(GatewayOriginPolicy.isIpAddress(ok)).as(ok).isTrue();
        }
        for (String bad : List.of("256.1.1.1", "1.2.3", "1.2.3.4.5", "01.2.3.4", "1.2.3.-4", "a.b.c.d", "1..3.4", " 1.2.3.4", "1.2.3.4 ")) {
            assertThat(GatewayOriginPolicy.isIpAddress(bad)).as("[" + bad + "]").isFalse();
        }
    }

    // ------------------------------------------------------------------ 비밀 값 비교와 설정 점검

    @Test
    @DisplayName("비밀 값 비교: 같으면 true, 다르거나 비었거나 null이면 false")
    void secretComparison() {
        assertThat(GatewayOriginPolicy.secretMatches(SECRET, SECRET)).isTrue();
        assertThat(GatewayOriginPolicy.secretMatches(SECRET, SECRET.toUpperCase())).as("대소문자를 구분한다").isFalse();
        assertThat(GatewayOriginPolicy.secretMatches(SECRET, null)).isFalse();
        assertThat(GatewayOriginPolicy.secretMatches("", "")).as("비어 있는 비밀 값은 아무것과도 같지 않다").isFalse();
        assertThat(GatewayOriginPolicy.secretMatches(null, "x")).isFalse();
    }

    @Test
    @DisplayName("켰는데 비밀 값이 없거나 32자 미만이면 서버 시작을 막는다. 꺼져 있으면 비밀 값이 없어도 된다")
    void startupProblems() {
        assertThat(GatewayOriginPolicy.settingsProblems(true, SECRET, 1).isEmpty()).isTrue();
        assertThat(GatewayOriginPolicy.settingsProblems(false, "", 1).isEmpty()).as("꺼져 있으면 점검하지 않음").isTrue();
        assertThat(GatewayOriginPolicy.settingsProblems(true, "", 1).size()).isEqualTo(1);
        assertThat(GatewayOriginPolicy.settingsProblems(true, null, 1).size()).isEqualTo(1);
        assertThat(GatewayOriginPolicy.settingsProblems(true, "x".repeat(31), 1).size()).as("31자").isEqualTo(1);
        assertThat(GatewayOriginPolicy.settingsProblems(true, "x".repeat(32), 1).isEmpty()).as("32자").isTrue();
    }

    @Test
    @DisplayName("proxy-hops가 1~5를 벗어나면 서버 시작을 막는다 (꺼져 있어도)")
    void hopsMustBeInRange() {
        assertThat(GatewayOriginPolicy.settingsProblems(false, "", 0).size()).isEqualTo(1);
        assertThat(GatewayOriginPolicy.settingsProblems(false, "", 6).size()).isEqualTo(1);
        assertThat(GatewayOriginPolicy.settingsProblems(false, "", 5).isEmpty()).isTrue();
        assertThat(GatewayOriginPolicy.settingsProblems(true, SECRET, 2).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("설정 오류 메시지에 비밀 값이 들어가지 않는다")
    void problemMessagesNeverContainTheSecret() {
        String secret = "short-but-secret";
        for (String message : GatewayOriginPolicy.settingsProblems(true, secret, 1)) {
            assertThat(message.contains(secret)).isFalse();
        }
    }
}