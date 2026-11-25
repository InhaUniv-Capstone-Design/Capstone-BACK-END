package com.gamcho.yakyeon.domain.notification.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DoseAlertRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-29T12:00:00+09:00");

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-10-29T" + time + "+09:00");
    }

    @Test
    @DisplayName("복용 시간 알림: 예정 10분 전부터 정각 직전까지만 대상이다")
    void reminderWindow() {
        assertThat(DoseAlertRules.isReminderDue(at("12:05:00"), NOW)).as("5분 뒤").isTrue();
        assertThat(DoseAlertRules.isReminderDue(at("12:10:00"), NOW)).as("정확히 10분 뒤(포함)").isTrue();
        assertThat(DoseAlertRules.isReminderDue(at("12:00:01"), NOW)).as("1초 뒤").isTrue();
        assertThat(DoseAlertRules.isReminderDue(at("12:10:01"), NOW)).as("10분 1초 뒤(아직 이르다)").isFalse();
        assertThat(DoseAlertRules.isReminderDue(at("12:00:00"), NOW)).as("정각은 약통 담당").isFalse();
        assertThat(DoseAlertRules.isReminderDue(at("11:59:59"), NOW)).as("이미 지난 예정").isFalse();
    }

    @Test
    @DisplayName("본인 미복용 알림: 예정 30분이 지났고 마감 전이면 대상이다")
    void selfMissedWindow() {
        OffsetDateTime close = at("14:30:00"); // 예정 11:30의 마감(3시간 뒤)
        assertThat(DoseAlertRules.isSelfMissedDue(at("11:30:00"), close, NOW)).as("정확히 30분 지남(포함)").isTrue();
        assertThat(DoseAlertRules.isSelfMissedDue(at("11:00:00"), at("14:00:00"), NOW)).as("1시간 지남").isTrue();
        assertThat(DoseAlertRules.isSelfMissedDue(at("11:31:00"), close, NOW)).as("29분 지남(아직 이르다)").isFalse();
        assertThat(DoseAlertRules.isSelfMissedDue(at("11:30:00"), at("12:00:00"), NOW)).as("마감과 같은 시각").isFalse();
        assertThat(DoseAlertRules.isSelfMissedDue(at("11:30:00"), at("12:00:01"), NOW)).as("마감 1초 전").isTrue();
        assertThat(DoseAlertRules.isSelfMissedDue(at("08:00:00"), at("11:00:00"), NOW)).as("마감이 지남(누락 확정 중)").isFalse();
    }

    @Test
    @DisplayName("SQL에 넘기는 기준 시각은 판정 함수와 같은 경계를 쓴다")
    void boundariesMatch() {
        assertThat(DoseAlertRules.reminderUntil(NOW)).isEqualTo(at("12:10:00"));
        assertThat(DoseAlertRules.selfMissedCutoff(NOW)).isEqualTo(at("11:30:00"));
    }

    @Test
    @DisplayName("본인 미복용 알림(30분)은 보호자 미복용 알림(1시간)보다 먼저 간다")
    void selfAlertComesBeforeGuardianAlert() {
        assertThat(DoseAlertRules.SELF_MISSED_AFTER.compareTo(MissedDoseRules.ALERT_AFTER) < 0).isTrue();
        assertThat(DoseAlertRules.REMINDER_BEFORE.toMinutes()).isEqualTo(10L);
        assertThat(DoseAlertRules.SELF_MISSED_AFTER.toMinutes()).isEqualTo(30L);
    }
}