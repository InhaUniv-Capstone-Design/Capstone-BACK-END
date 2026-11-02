package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceSyncRulesTest {

    @Test
    @DisplayName("약통이 받은 버전이 서버 최신 버전과 같으면 최신이다")
    void equalVersions() {
        assertThat(DeviceSyncRules.isInSync(7, 7)).isTrue();
    }

    @Test
    @DisplayName("서버 버전이 더 높으면 약통이 아직 새 일정을 받지 못한 것이다")
    void serverAhead() {
        assertThat(DeviceSyncRules.isInSync(8, 7)).isFalse();
        assertThat(DeviceSyncRules.isInSync(1, 0)).isFalse();
    }

    @Test
    @DisplayName("약통이 받은 버전이 더 높아도(중계 서버가 별도 번호를 쓰는 경우) 최신으로 본다")
    void deviceAhead() {
        assertThat(DeviceSyncRules.isInSync(0, 9)).isTrue();
        assertThat(DeviceSyncRules.isInSync(3, 4)).isTrue();
    }

    @Test
    @DisplayName("한 번도 일정을 주고받지 않은 약통(둘 다 0 또는 비어 있음)은 맞출 것이 없으니 최신이다")
    void neverSynced() {
        assertThat(DeviceSyncRules.isInSync(0, 0)).isTrue();
        assertThat(DeviceSyncRules.isInSync(null, null)).isTrue();
        assertThat(DeviceSyncRules.isInSync(null, 0)).isTrue();
    }

    @Test
    @DisplayName("서버 버전만 있고 약통은 아직 보고하지 않았으면 최신이 아니다")
    void deviceNeverReported() {
        assertThat(DeviceSyncRules.isInSync(2, null)).isFalse();
    }
}