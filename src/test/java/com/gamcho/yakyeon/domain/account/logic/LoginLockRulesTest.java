package com.gamcho.yakyeon.domain.account.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginLockRulesTest {

    @Test
    @DisplayName("기본값 5번, 15분은 그대로 쓴다")
    void defaults() {
        assertThat(LoginLockRules.maxFailures(5)).isEqualTo(5);
        assertThat(LoginLockRules.lockMinutes(15)).isEqualTo(15);
    }

    @Test
    @DisplayName("실패 한도는 2~20번이다 (한 번만 틀려도 잠기거나 사실상 안 잠기는 설정 실수를 막는다)")
    void failureBounds() {
        assertThat(LoginLockRules.maxFailures(1)).isEqualTo(2);
        assertThat(LoginLockRules.maxFailures(0)).isEqualTo(2);
        assertThat(LoginLockRules.maxFailures(-3)).isEqualTo(2);
        assertThat(LoginLockRules.maxFailures(20)).isEqualTo(20);
        assertThat(LoginLockRules.maxFailures(21)).isEqualTo(20);
        assertThat(LoginLockRules.maxFailures(1_000_000)).isEqualTo(20);
    }

    @Test
    @DisplayName("잠금 시간은 1분~24시간이다 (잠금이 없거나 영원한 설정 실수를 막는다)")
    void minuteBounds() {
        assertThat(LoginLockRules.lockMinutes(0)).isEqualTo(1);
        assertThat(LoginLockRules.lockMinutes(-10)).isEqualTo(1);
        assertThat(LoginLockRules.lockMinutes(1)).isEqualTo(1);
        assertThat(LoginLockRules.lockMinutes(1440)).isEqualTo(1440);
        assertThat(LoginLockRules.lockMinutes(1441)).isEqualTo(1440);
        assertThat(LoginLockRules.lockMinutes(Integer.MAX_VALUE)).isEqualTo(1440);
    }
}