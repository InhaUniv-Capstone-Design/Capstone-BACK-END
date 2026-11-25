package com.gamcho.yakyeon.domain.device.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DoseMatcherTest {

    private static final Duration EARLIEST = Duration.ofHours(6);

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-11-01T" + time + "+09:00");
    }

    /** 예정 시각 s, 정상 인정은 s+30분, 마감은 s+3시간 (서비스의 기본 규칙) */
    private static DoseMatcher.Candidate dose(long id, String slot, String scheduled) {
        OffsetDateTime s = at(scheduled);
        return new DoseMatcher.Candidate(id, slot, s, s.plusMinutes(30), s.plusHours(3));
    }

    @Test
    @DisplayName("예정 시각 30분 안에 먹으면 정상")
    void withinNormalWindow() {
        Optional<DoseMatcher.Match> match = DoseMatcher.choose(List.of(dose(1, "MORNING", "08:00:00")), at("08:20:00"), EARLIEST);
        assertThat(match.isPresent()).isTrue();
        assertThat(match.get().doseId()).isEqualTo(1L);
        assertThat(match.get().judgement()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("정상 인정 시각을 지나 마감 전에 먹으면 지연")
    void afterNormalWindowIsDelayed() {
        assertThat(DoseMatcher.choose(List.of(dose(1, "MORNING", "08:00:00")), at("09:00:00"), EARLIEST).get().judgement())
                .isEqualTo("DELAYED");
    }

    @Test
    @DisplayName("정상 인정 시각 정각은 정상, 1초 지나면 지연")
    void normalBoundary() {
        List<DoseMatcher.Candidate> doses = List.of(dose(1, "MORNING", "08:00:00"));
        assertThat(DoseMatcher.choose(doses, at("08:30:00"), EARLIEST).get().judgement()).isEqualTo("NORMAL");
        assertThat(DoseMatcher.choose(doses, at("08:30:01"), EARLIEST).get().judgement()).isEqualTo("DELAYED");
    }

    @Test
    @DisplayName("예정보다 일찍 먹은 것은 정상이다 (6시간 안이면 그 예정으로 본다)")
    void earlyIsNormal() {
        Optional<DoseMatcher.Match> match = DoseMatcher.choose(List.of(dose(1, "DINNER", "18:00:00")), at("15:00:00"), EARLIEST);
        assertThat(match.get().judgement()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("예정이 6시간보다 더 멀면 그 예정으로 보지 않는다 (예정 밖 복용)")
    void tooFarAhead() {
        assertThat(DoseMatcher.choose(List.of(dose(1, "DINNER", "18:00:00")), at("11:59:59"), EARLIEST).isPresent()).isFalse();
        assertThat(DoseMatcher.choose(List.of(dose(1, "DINNER", "18:00:00")), at("12:00:00"), EARLIEST).isPresent()).isTrue();
    }

    @Test
    @DisplayName("마감이 지난 예정은 고르지 않는다 (마감 시각 정각도 지난 것으로 본다)")
    void closedDoseIsNotChosen() {
        List<DoseMatcher.Candidate> doses = List.of(dose(1, "MORNING", "08:00:00"));
        assertThat(DoseMatcher.choose(doses, at("10:59:59"), EARLIEST).isPresent()).isTrue();
        assertThat(DoseMatcher.choose(doses, at("11:00:00"), EARLIEST).isPresent()).isFalse();
    }

    @Test
    @DisplayName("먹을 수 있는 예정이 여럿이면 가장 이른 예정을 고른다")
    void earliestFirst() {
        Optional<DoseMatcher.Match> match = DoseMatcher.choose(
                List.of(dose(2, "DINNER", "18:00:00"), dose(1, "MORNING", "08:00:00")), at("08:10:00"), EARLIEST);
        assertThat(match.get().doseId()).isEqualTo(1L);
        assertThat(match.get().slot()).isEqualTo("MORNING");
    }

    @Test
    @DisplayName("예정 시각이 같으면 예정 번호가 작은 쪽을 고른다")
    void tieBreaksByDoseId() {
        Optional<DoseMatcher.Match> match = DoseMatcher.choose(
                List.of(dose(9, "MORNING", "08:00:00"), dose(4, "MORNING", "08:00:00")), at("08:05:00"), EARLIEST);
        assertThat(match.get().doseId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("후보가 없으면 예정 밖 복용이다")
    void noCandidates() {
        assertThat(DoseMatcher.choose(List.of(), at("08:00:00"), EARLIEST).isPresent()).isFalse();
    }
}