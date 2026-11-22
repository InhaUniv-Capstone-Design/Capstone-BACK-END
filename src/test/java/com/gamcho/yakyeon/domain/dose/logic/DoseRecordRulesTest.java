package com.gamcho.yakyeon.domain.dose.logic;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DoseRecordRulesTest {

    private static final OffsetDateTime SCHEDULED = OffsetDateTime.of(2026, 11, 20, 12, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime NOW = SCHEDULED.plusHours(1);

    @Test
    void 예정_3시간_전부터_기록할_수_있다() {
        assertThat(DoseRecordRules.EARLIEST_BEFORE_SCHEDULED.toHours()).isEqualTo(3);
        assertThat(DoseRecordRules.takenAtViolation(SCHEDULED.minusHours(3), SCHEDULED, NOW)).as("정확히 3시간 전").isEmpty();
        assertThat(DoseRecordRules.takenAtViolation(SCHEDULED.minusHours(3).plusSeconds(1), SCHEDULED, NOW)).isEmpty();
    }

    @Test
    void 예정_3시간보다_이른_복용_시각은_거절한다() {
        assertThat(DoseRecordRules.takenAtViolation(SCHEDULED.minusHours(3).minusSeconds(1), SCHEDULED, NOW))
                .hasValueSatisfying(m -> assertThat(m).contains("3시간 전"));
        assertThat(DoseRecordRules.takenAtViolation(SCHEDULED.minusHours(6), SCHEDULED, NOW)).isPresent();
    }

    @Test
    void 미래_시각은_거절한다() {
        assertThat(DoseRecordRules.takenAtViolation(NOW.plusSeconds(1), SCHEDULED, NOW)).isPresent();
    }
}