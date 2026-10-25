package com.gamcho.yakyeon.domain.dose.logic;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "있어야 하는 예정"(PlannedDose)과 "지금 DB에 있는 예정"(ExistingDose)을 맞춰서
 * 무엇을 지우고 무엇을 만들지 정한다 (DB·스프링 없는 순수 로직). 몇 번을 실행해도 결과가 같다(멱등).
 *
 * 규칙 (예정은 처방·시간대·한국 날짜마다 하나다):
 *  - 이미 판정된(PENDING이 아닌) 예정은 절대 지우거나 바꾸지 않는다 - 복용 기록이기 때문이다.
 *  - 더 이상 필요 없는 예정(약이 끝났거나 삭제됨, 그 시간대의 시각이 없어짐)은 판정 전일 때만 지운다.
 *  - 복용 시각이 바뀌어서 시각이 달라진 예정은, 아직 시각이 안 지난 것만 지우고 새 시각으로 다시 만든다.
 *    이미 시각이 지난 예정(진행 중)은 그대로 둔다 - 지금 먹고 있을 수 있고, 같은 날 같은 시간대에 예정이 둘이 되면 안 된다.
 *  - 만들 예정은 마감이 지난 것은 건너뛴다 (예: 오후에 등록했는데 아침 예정이 바로 "누락"이 되면 안 된다).
 */
public final class DoseReconciler {

    private DoseReconciler() {
    }

    public static ReconcileResult reconcile(List<PlannedDose> planned, List<ExistingDose> existing,
                                            OffsetDateTime now, ZoneId zone) {
        Map<String, PlannedDose> plannedByKey = new HashMap<>();
        for (PlannedDose p : planned) {
            plannedByKey.put(key(p.slot(), p.date()), p);
        }

        Set<String> occupied = new HashSet<>();   // 이미 예정이 자리 잡고 있는 (시간대, 날짜)
        List<Long> deleteIds = new ArrayList<>();
        for (ExistingDose e : existing) {
            String key = key(e.slot(), e.scheduledAt().atZoneSameInstant(zone).toLocalDate());
            PlannedDose p = plannedByKey.get(key);
            if (p == null) {
                // 더 이상 필요 없는 예정. 판정된 기록은 보관하고, 판정 전인 것만 지운다
                if (e.pending()) {
                    deleteIds.add(e.doseId());
                } else {
                    occupied.add(key);
                }
                continue;
            }
            boolean same = e.scheduledAt().isEqual(p.scheduledAt()) && e.settingId() == p.settingId();
            if (same) {
                occupied.add(key);
            } else if (e.pending() && e.scheduledAt().isAfter(now)) {
                deleteIds.add(e.doseId());        // 시각이 바뀌었고 아직 안 지난 예정 → 새 시각으로 다시 만든다
            } else {
                occupied.add(key);                 // 이미 지났거나 판정된 예정은 그대로 둔다
            }
        }

        List<PlannedDose> inserts = new ArrayList<>();
        for (PlannedDose p : planned) {
            if (!occupied.contains(key(p.slot(), p.date())) && p.closeAt().isAfter(now)) {
                inserts.add(p);
            }
        }
        return new ReconcileResult(deleteIds, inserts);
    }

    private static String key(String slot, LocalDate date) {
        return slot + "|" + date;
    }
}