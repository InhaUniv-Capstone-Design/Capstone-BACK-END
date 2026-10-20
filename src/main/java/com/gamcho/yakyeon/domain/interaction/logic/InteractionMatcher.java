package com.gamcho.yakyeon.domain.interaction.logic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 복약자의 약 목록과 DUR에서 찾은 제품 쌍을 맞춰서 병용금기 목록을 만든다 (DB·스프링 없는 순수 로직).
 */
public final class InteractionMatcher {

    private InteractionMatcher() {
    }

    /**
     * 두 약이 함께 복용될 수 있는지. 이미 끝난 약이 끼어 있거나 복용 기간이 서로 겹치지 않으면
     * 같이 먹는 일이 없으므로 병용금기로 보지 않는다 (복용 기간 겹침은 양 끝 날짜를 포함해서 본다).
     */
    public static boolean isRelevantPair(MedicationRef a, MedicationRef b, LocalDate today) {
        if (a.endDate().isBefore(today) || b.endDate().isBefore(today)) {
            return false;
        }
        return !a.startDate().isAfter(b.endDate()) && !b.startDate().isAfter(a.endDate());
    }

    /**
     * @param meds  검사 대상 약 (끝난 약은 호출하는 쪽에서 이미 뺀 목록)
     * @param pairs DUR에서 찾은 제품 쌍 (복약자의 약 품목코드 안에서만 찾은 결과)
     * @return 약 번호 쌍(작은 번호가 A)과 DUR 번호 순으로 정렬한 병용금기 목록
     */
    public static List<Finding> match(List<MedicationRef> meds, List<FoundPair> pairs, LocalDate today) {
        Map<String, List<MedicationRef>> byItemSeq = new LinkedHashMap<>();
        for (MedicationRef m : meds) {
            if (m.checkable()) {
                byItemSeq.computeIfAbsent(m.itemSeq(), k -> new ArrayList<>()).add(m);
            }
        }

        Set<String> seen = new HashSet<>();
        List<Finding> result = new ArrayList<>();
        for (FoundPair p : pairs) {
            for (MedicationRef ma : byItemSeq.getOrDefault(p.itemSeqA(), List.of())) {
                for (MedicationRef mb : byItemSeq.getOrDefault(p.itemSeqB(), List.of())) {
                    if (ma.medicationId() == mb.medicationId() || !isRelevantPair(ma, mb, today)) {
                        continue;
                    }
                    // 약 번호가 작은 쪽을 A로 맞춘다. 성분 코드도 약과 같이 바꿔야 약-성분 짝이 유지된다.
                    boolean swap = ma.medicationId() > mb.medicationId();
                    Finding f = new Finding(
                            swap ? mb.medicationId() : ma.medicationId(),
                            swap ? ma.medicationId() : mb.medicationId(),
                            p.durSeq(),
                            swap ? p.ingredientCodeB() : p.ingredientCodeA(),
                            swap ? p.ingredientCodeA() : p.ingredientCodeB(),
                            p.typeCode(), p.reason(), p.remark(), p.sourceName());
                    // DB 유니크 키(검사, 약 A, 약 B, DUR 번호)와 같은 기준으로 중복을 막는다
                    if (seen.add(f.medicationAId() + "|" + f.medicationBId() + "|" + f.durSeq())) {
                        result.add(f);
                    }
                }
            }
        }
        result.sort(Comparator.comparingLong(Finding::medicationAId)
                .thenComparingLong(Finding::medicationBId)
                .thenComparing(Finding::durSeq));
        return result;
    }
}