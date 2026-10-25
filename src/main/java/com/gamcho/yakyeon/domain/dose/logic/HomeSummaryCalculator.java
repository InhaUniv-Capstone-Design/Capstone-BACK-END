package com.gamcho.yakyeon.domain.dose.logic;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 오늘의 복약 현황 집계 (DB·스프링 없는 순수 로직). 홈 화면의 "복약 현황 2/3", 아침·점심·저녁 아이콘,
 * "다음 복약"이 모두 여기서 나온다.
 *
 * 시간대 아이콘의 상태 (그날 그 시간대의 예정들을 합쳐서 하나로):
 *  NONE    - 그 시간대 예정이 없다
 *  MISSED  - 하나라도 누락이다 (다른 처방은 먹었어도 놓친 게 있다는 걸 숨기지 않는다)
 *  DONE    - 모두 복용했다 (정상 또는 지연)
 *  PENDING - 아직 판정 전인 예정이 있고, 누락은 없다
 */
public final class HomeSummaryCalculator {

    public static final List<String> SLOTS = List.of("MORNING", "LUNCH", "DINNER");

    private HomeSummaryCalculator() {
    }

    public record SlotState(String slot, String state) {
    }

    /** @param nextDoseId 지금 이후에 먹어야 할 가장 이른 예정(마감 전 + 판정 전). 없으면 null */
    public record Summary(int total, int normal, int delayed, int missed, int pending,
                          List<SlotState> slots, Long nextDoseId) {

        /** 복용을 마친 예정 수 (정상 + 지연) */
        public int done() {
            return normal + delayed;
        }
    }

    public static Summary compute(List<DoseBrief> doses, OffsetDateTime now) {
        int normal = 0;
        int delayed = 0;
        int missed = 0;
        int pending = 0;
        for (DoseBrief d : doses) {
            switch (d.status()) {
                case "NORMAL" -> normal++;
                case "DELAYED" -> delayed++;
                case "MISSED" -> missed++;
                default -> pending++;
            }
        }

        List<SlotState> slots = new ArrayList<>();
        for (String slot : SLOTS) {
            List<DoseBrief> inSlot = doses.stream().filter(d -> d.slot().equals(slot)).toList();
            String state;
            if (inSlot.isEmpty()) {
                state = "NONE";
            } else if (inSlot.stream().anyMatch(d -> d.status().equals("MISSED"))) {
                state = "MISSED";
            } else if (inSlot.stream().allMatch(d -> d.status().equals("NORMAL") || d.status().equals("DELAYED"))) {
                state = "DONE";
            } else {
                state = "PENDING";
            }
            slots.add(new SlotState(slot, state));
        }

        Optional<DoseBrief> next = doses.stream()
                .filter(d -> d.status().equals("PENDING") && d.closeAt().isAfter(now))
                .min(Comparator.comparing(DoseBrief::scheduledAt).thenComparingLong(DoseBrief::doseId));
        return new Summary(doses.size(), normal, delayed, missed, pending, slots, next.map(DoseBrief::doseId).orElse(null));
    }
}