package com.gamcho.yakyeon.domain.device.logic;

import com.gamcho.yakyeon.domain.dose.logic.DoseRecordRules;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 약통에서 복용이 확인됐을 때 "지금 먹은 게 어느 예정인지" 정한다 (스프링·DB 없는 순수 로직).
 *
 * 그 칸의 약봉지(처방)에서 아직 판정되지 않았고, 마감 전이고, 예정 시각이 너무 멀지 않은(기본 6시간 안) 예정 중
 * 가장 이른 것이 먹은 예정이다. 없으면 예정 밖(UNSCHEDULED) 복용이다. 정상/지연은 앱에서 기록할 때와 같은 기준으로
 * 복용 시각이 정상 인정 시각까지면 정상, 지나면 지연이다.
 */
public final class DoseMatcher {

    private DoseMatcher() {
    }

    public record Candidate(long doseId, String slot, OffsetDateTime scheduledAt,
                            OffsetDateTime normalUntil, OffsetDateTime closeAt) {
    }

    public record Match(long doseId, String slot, String judgement) {
    }

    /** @param earliest 예정 시각보다 이만큼까지 일찍 먹은 것은 그 예정으로 본다 */
    public static Optional<Match> choose(List<Candidate> candidates, OffsetDateTime takenAt, Duration earliest) {
        return candidates.stream()
                .filter(c -> c.closeAt().isAfter(takenAt) && !c.scheduledAt().isAfter(takenAt.plus(earliest)))
                .min(Comparator.comparing(Candidate::scheduledAt).thenComparingLong(Candidate::doseId))
                .map(c -> new Match(c.doseId(), c.slot(), DoseRecordRules.statusFor(takenAt, c.normalUntil())));
    }
}