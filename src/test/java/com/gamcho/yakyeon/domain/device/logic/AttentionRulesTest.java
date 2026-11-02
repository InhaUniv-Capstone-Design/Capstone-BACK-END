package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttentionRulesTest {

    @Test
    @DisplayName("열림만 있음을 확인 필요로 보는 기간은 60분이다")
    void windowIsSixtyMinutes() {
        assertThat(AttentionRules.WINDOW.toMinutes()).isEqualTo(60L);
    }

    @Test
    @DisplayName("기준 시각은 지금에서 60분 전이다")
    void sinceIsSixtyMinutesAgo() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-02T10:00:00+09:00");
        assertThat(AttentionRules.since(now)).isEqualTo(OffsetDateTime.parse("2026-11-02T09:00:00+09:00"));
    }

    @Test
    @DisplayName("앱에 내려가는 표시 값은 OPENED다")
    void displayValue() {
        assertThat(AttentionRules.OPENED).isEqualTo("OPENED");
    }
}