package com.gamcho.yakyeon.domain.account.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurgePolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-10T12:00:00+09:00");

    @Test
    @DisplayName("기본 정책: 복약자 본인은 바로(0일), 대리인 계정은 30일, 관리자 없는 복약자는 30일")
    void defaultPolicy() {
        PurgePolicy p = PurgePolicy.of(0, 30, 30, 200);
        assertThat(p.patientRetentionDays()).isEqualTo(0);
        assertThat(p.guardianRetentionDays()).isEqualTo(30);
        assertThat(p.abandonedPatientDays()).isEqualTo(30);
        assertThat(p.batchLimit()).isEqualTo(200);
    }

    @Test
    @DisplayName("기준 시각은 지금에서 보관 일수를 뺀 값이다 (0일이면 지금)")
    void cutoffs() {
        PurgePolicy p = PurgePolicy.of(0, 30, 30, 200);
        assertThat(p.patientCutoff(NOW)).isEqualTo(NOW);
        assertThat(p.guardianCutoff(NOW)).isEqualTo(OffsetDateTime.parse("2026-10-11T12:00:00+09:00"));
        assertThat(p.abandonedCutoff(NOW)).isEqualTo(OffsetDateTime.parse("2026-10-11T12:00:00+09:00"));
    }

    @Test
    @DisplayName("음수 일수는 0으로 고친다 (미래 시각이 기준이 되어 방금 탈퇴한 것까지 지우는 일이 없게, 그리고 기준이 앞당겨지지 않게)")
    void negativeDaysAreClamped() {
        PurgePolicy p = PurgePolicy.of(-5, -1, -3, 50);
        assertThat(p.patientRetentionDays()).isEqualTo(0);
        assertThat(p.guardianRetentionDays()).isEqualTo(0);
        assertThat(p.abandonedPatientDays()).isEqualTo(0);
        assertThat(p.patientCutoff(NOW)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("너무 큰 값은 상한으로 줄인다")
    void hugeValuesAreCapped() {
        PurgePolicy p = PurgePolicy.of(99_999, 99_999, 99_999, 99_999);
        assertThat(p.patientRetentionDays()).isEqualTo(PurgePolicy.MAX_DAYS);
        assertThat(p.guardianRetentionDays()).isEqualTo(PurgePolicy.MAX_DAYS);
        assertThat(p.abandonedPatientDays()).isEqualTo(PurgePolicy.MAX_DAYS);
        assertThat(p.batchLimit()).isEqualTo(PurgePolicy.MAX_BATCH);
    }

    @Test
    @DisplayName("한 번에 처리하는 수는 1~1000이다")
    void batchLimitBounds() {
        assertThat(PurgePolicy.of(0, 30, 30, 0).batchLimit()).isEqualTo(1);
        assertThat(PurgePolicy.of(0, 30, 30, -10).batchLimit()).isEqualTo(1);
        assertThat(PurgePolicy.of(0, 30, 30, 1000).batchLimit()).isEqualTo(1000);
        assertThat(PurgePolicy.of(0, 30, 30, 1001).batchLimit()).isEqualTo(1000);
    }

    @Test
    @DisplayName("관리자 없는 복약자 보관 일수는 대리인 계정 보관 일수를 넘지 못한다 (넘으면 판단 근거인 연동 기록이 먼저 사라진다)")
    void abandonedDaysCannotExceedGuardianRetention() {
        PurgePolicy p = PurgePolicy.of(0, 30, 90, 200);
        assertThat(p.abandonedPatientDays()).isEqualTo(30);
        PurgePolicy q = PurgePolicy.of(0, 60, 30, 200);
        assertThat(q.abandonedPatientDays()).as("더 짧게는 가능").isEqualTo(30);
        PurgePolicy r = PurgePolicy.of(0, 0, 30, 200);
        assertThat(r.abandonedPatientDays()).isEqualTo(0);
    }
}