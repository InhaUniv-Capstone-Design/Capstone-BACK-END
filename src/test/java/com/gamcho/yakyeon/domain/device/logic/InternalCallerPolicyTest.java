package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InternalCallerPolicyTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static boolean trusted(String configured, String provided, String remote, boolean xff, boolean origin) {
        return InternalCallerPolicy.isTrusted(configured, provided, remote, xff, origin);
    }

    @Test
    @DisplayName("같은 서버(127.0.0.1)에서 맞는 키로 직접 부르면 믿는다")
    void trustsLocalCallerWithRightKey() {
        assertThat(trusted(KEY, KEY, "127.0.0.1", false, false)).isTrue();
        assertThat(trusted(KEY, KEY, "127.0.1.1", false, false)).isTrue();
        assertThat(trusted(KEY, KEY, "::1", false, false)).isTrue();
        assertThat(trusted(KEY, KEY, "0:0:0:0:0:0:0:1", false, false)).isTrue();
        assertThat(trusted(KEY, KEY, "::ffff:127.0.0.1", false, false)).isTrue();
    }

    @Test
    @DisplayName("키 앞뒤 공백(환경 파일의 줄바꿈 등)은 무시한다")
    void ignoresSurroundingWhitespace() {
        assertThat(trusted(KEY + "\n", " " + KEY, "127.0.0.1", false, false)).isTrue();
    }

    @Test
    @DisplayName("키가 없거나 틀리거나 한 글자만 달라도 거절한다")
    void rejectsMissingOrWrongKey() {
        assertThat(trusted(KEY, null, "127.0.0.1", false, false)).isFalse();
        assertThat(trusted(KEY, "", "127.0.0.1", false, false)).isFalse();
        assertThat(trusted(KEY, "   ", "127.0.0.1", false, false)).isFalse();
        assertThat(trusted(KEY, KEY.substring(0, KEY.length() - 1) + "0", "127.0.0.1", false, false)).isFalse();
        assertThat(trusted(KEY, KEY.substring(1), "127.0.0.1", false, false)).isFalse();
        assertThat(trusted(KEY, KEY + "x", "127.0.0.1", false, false)).isFalse();
    }

    @Test
    @DisplayName("서버에 키가 설정돼 있지 않거나 짧으면 어떤 호출도 거절한다 (설정 실수가 '아무나 통과'가 되지 않게)")
    void rejectsEverythingWithoutUsableConfiguredKey() {
        assertThat(trusted(null, "", "127.0.0.1", false, false)).isFalse();
        assertThat(trusted("", "", "127.0.0.1", false, false)).isFalse();
        assertThat(trusted("   ", "   ", "127.0.0.1", false, false)).isFalse();
        assertThat(trusted("short-key", "short-key", "127.0.0.1", false, false)).isFalse();
        assertThat(InternalCallerPolicy.isUsableKey(KEY)).isTrue();
        assertThat(InternalCallerPolicy.isUsableKey("x".repeat(31))).isFalse();
        assertThat(InternalCallerPolicy.isUsableKey("x".repeat(32))).isTrue();
    }

    @Test
    @DisplayName("맞는 키여도 외부 주소에서 온 요청은 거절한다")
    void rejectsNonLoopbackAddress() {
        assertThat(trusted(KEY, KEY, "10.0.0.5", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "203.0.113.9", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "2001:db8::1", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, null, false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "", false, false)).isFalse();
    }

    @Test
    @DisplayName("루프백처럼 보이는 가짜 주소(이름, 덧붙인 글자, 범위 밖 숫자)는 거절한다")
    void rejectsLookAlikeAddresses() {
        assertThat(trusted(KEY, KEY, "127.0.0.1.evil.com", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "localhost", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "127.0.0.256", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "127.0.0.01", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "1127.0.0.1", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "127.0.0.1:8080", false, false)).isFalse();
        assertThat(trusted(KEY, KEY, "::2", false, false)).isFalse();
    }

    @Test
    @DisplayName("프록시를 거쳤다는 헤더(X-Forwarded-For, X-Origin-Verify)가 있으면 접속 주소가 루프백이어도 거절한다")
    void rejectsProxiedRequests() {
        assertThat(trusted(KEY, KEY, "127.0.0.1", true, false)).isFalse();
        assertThat(trusted(KEY, KEY, "127.0.0.1", false, true)).isFalse();
        assertThat(trusted(KEY, KEY, "127.0.0.1", true, true)).isFalse();
    }

    @Test
    @DisplayName("터무니없이 긴 키로는 해시 계산을 시키지 않고 거절한다")
    void rejectsOversizedKey() {
        assertThat(trusted(KEY, "a".repeat(300), "127.0.0.1", false, false)).isFalse();
    }
}