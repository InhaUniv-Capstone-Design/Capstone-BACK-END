package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceInventoryRulesTest {

    private static final String SECRET = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8";
    private static final String INVENTORY_HASH = DeviceSecrets.hash(SECRET);

    @Test
    @DisplayName("출고 목록의 해시와 같은 비밀값이면 등록할 수 있다")
    void correctSecretIsRegistrable() {
        assertThat(DeviceInventoryRules.isRegistrable(SECRET, INVENTORY_HASH)).isTrue();
    }

    @Test
    @DisplayName("비밀값이 다르면 등록할 수 없다 (한 글자만 달라도)")
    void wrongSecretIsRejected() {
        assertThat(DeviceInventoryRules.isRegistrable(SECRET + "x", INVENTORY_HASH)).isFalse();
        assertThat(DeviceInventoryRules.isRegistrable("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r9", INVENTORY_HASH)).isFalse();
        assertThat(DeviceInventoryRules.isRegistrable(SECRET.toUpperCase(), INVENTORY_HASH)).as("대소문자 구분").isFalse();
        assertThat(DeviceInventoryRules.isRegistrable(" " + SECRET, INVENTORY_HASH)).as("앞 공백").isFalse();
    }

    @Test
    @DisplayName("출고 목록에 없는 시리얼(해시 null)은 어떤 비밀값이어도 등록할 수 없다")
    void unknownSerialIsRejected() {
        assertThat(DeviceInventoryRules.isRegistrable(SECRET, null)).isFalse();
        assertThat(DeviceInventoryRules.isRegistrable("no-such-device-placeholder-secret-0000000000", null))
                .as("자리표시 비밀값을 알아도 통과하면 안 된다").isFalse();
    }

    @Test
    @DisplayName("비밀값이 없으면(null) 등록할 수 없다")
    void nullSecretIsRejected() {
        assertThat(DeviceInventoryRules.isRegistrable(null, INVENTORY_HASH)).isFalse();
        assertThat(DeviceInventoryRules.isRegistrable(null, null)).isFalse();
    }

    @Test
    @DisplayName("출고 목록의 해시가 이상한 값(대문자, 짧음)이면 맞는 비밀값이어도 통과하지 않는다")
    void malformedInventoryHashDoesNotMatch() {
        assertThat(DeviceInventoryRules.isRegistrable(SECRET, INVENTORY_HASH.toUpperCase())).as("대문자 해시").isFalse();
        assertThat(DeviceInventoryRules.isRegistrable(SECRET, INVENTORY_HASH.substring(0, 63))).as("짧은 해시").isFalse();
        assertThat(DeviceInventoryRules.isRegistrable(SECRET, "")).isFalse();
    }

    @Test
    @DisplayName("서버가 저장하는 해시와 출고 목록의 해시 형식이 같다 (SHA-256 소문자 16진수 64자)")
    void hashFormatMatchesTheDatabaseRule() {
        assertThat(INVENTORY_HASH.matches("^[0-9a-f]{64}$")).isTrue();
        // 알려진 값: SHA-256("abc")
        assertThat(DeviceSecrets.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}