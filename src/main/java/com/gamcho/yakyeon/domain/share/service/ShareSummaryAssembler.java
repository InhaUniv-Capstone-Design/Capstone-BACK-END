package com.gamcho.yakyeon.domain.share.service;

import com.gamcho.yakyeon.domain.interaction.logic.Finding;
import com.gamcho.yakyeon.domain.interaction.logic.FoundPair;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionMatcher;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionTexts;
import com.gamcho.yakyeon.domain.interaction.logic.MedicationRef;
import com.gamcho.yakyeon.domain.share.dto.ShareSummaryResponse;
import com.gamcho.yakyeon.domain.share.logic.ShareSummaryRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 복약 이력 공유 요약을 조립한다 (스프링·DB 없음). 서비스가 읽어 온 값(복약자, 약, DUR 데이터)을 받아서
 * 복용 중/복약한 약물로 나누고, 공유 기간에 함께 복용한 병용금기 쌍을 찾는다. DB를 읽는 일은 서비스가 하고
 * 여기서는 규칙만 적용해서 입출력만으로 시험할 수 있다.
 */
final class ShareSummaryAssembler {

    private ShareSummaryAssembler() {
    }

    /**
     * 서비스가 읽어 온 약 한 개.
     *
     * @param endDate  계획한 종료일
     * @param endedOn  종료를 누른 한국 날짜 (누른 적이 없으면 null)
     * @param itemSeq  약물 DB와 연결된 약의 품목코드 (연결되지 않은 약은 null)
     */
    record MedicationInput(long medicationId, String name, String itemSeq, LocalDate startDate, LocalDate endDate,
                           LocalDate endedOn, String hospitalName) {
    }

    private record Taken(MedicationRef ref, String hospitalName, ShareSummaryRules.Kind kind) {
    }

    /**
     * @param datasetBaseDate 가장 최근에 성공한 DUR 데이터의 기준일 (아직 적재된 데이터가 없으면 비어 있다)
     * @param findPairs       품목코드 목록으로 DUR의 제품 쌍을 찾는 함수
     */
    static ShareSummaryResponse assemble(String patientName, LocalDate birthDate, ShareSummaryRules.Period period,
                                         List<MedicationInput> medications, Optional<LocalDate> datasetBaseDate,
                                         Function<List<String>, List<FoundPair>> findPairs) {
        List<Taken> taken = new ArrayList<>();
        for (MedicationInput m : medications) {
            LocalDate lastActive = ShareSummaryRules.lastActiveDate(m.endDate(), m.endedOn());
            ShareSummaryRules.Kind kind = ShareSummaryRules.classify(m.startDate(), lastActive, period);
            if (kind != ShareSummaryRules.Kind.NONE) {
                taken.add(new Taken(new MedicationRef(m.medicationId(), m.name(), m.itemSeq(), m.startDate(), lastActive),
                        m.hospitalName(), kind));
            }
        }

        return new ShareSummaryResponse(
                new ShareSummaryResponse.PatientInfo(patientName, birthDate),
                new ShareSummaryResponse.PeriodInfo(period.from(), period.to(), period.days()),
                interactions(taken, datasetBaseDate, period, findPairs),
                medicationItems(taken, ShareSummaryRules.Kind.CURRENT, Comparator
                        .comparing((ShareSummaryResponse.MedicationItem m) -> m.startDate())
                        .thenComparing(ShareSummaryResponse.MedicationItem::medicationId)),
                medicationItems(taken, ShareSummaryRules.Kind.PAST, Comparator
                        .comparing((ShareSummaryResponse.MedicationItem m) -> m.endDate()).reversed()
                        .thenComparing(ShareSummaryResponse.MedicationItem::medicationId)),
                InteractionTexts.SHARE_DISCLAIMER);
    }

    private static List<ShareSummaryResponse.MedicationItem> medicationItems(
            List<Taken> taken, ShareSummaryRules.Kind kind, Comparator<ShareSummaryResponse.MedicationItem> order) {
        return taken.stream()
                .filter(t -> t.kind() == kind)
                .map(t -> new ShareSummaryResponse.MedicationItem(t.ref().medicationId(), t.ref().name(),
                        t.ref().startDate(), t.ref().endDate(), t.hospitalName()))
                .sorted(order)
                .toList();
    }

    /**
     * 공유 기간 안에 함께 복용한 약 쌍 중 DUR에 등재된 것. DUR 데이터가 아직 적재되지 않았으면 확인할 수 없으므로
     * 항목은 비고 모든 약이 "확인하지 못한 약"으로 센다(없다는 말이 안전하다는 뜻으로 읽히지 않게 한다).
     */
    private static ShareSummaryResponse.InteractionSection interactions(
            List<Taken> taken, Optional<LocalDate> datasetBaseDate, ShareSummaryRules.Period period,
            Function<List<String>, List<FoundPair>> findPairs) {
        List<MedicationRef> refs = taken.stream().map(Taken::ref).toList();
        int unchecked = (int) refs.stream().filter(r -> datasetBaseDate.isEmpty() || !r.checkable()).count();
        if (datasetBaseDate.isEmpty()) {
            return new ShareSummaryResponse.InteractionSection(List.of(), unchecked);
        }

        List<String> itemSeqs = refs.stream().filter(MedicationRef::checkable).map(MedicationRef::itemSeq).distinct().toList();
        List<Finding> findings = InteractionMatcher.match(refs, findPairs.apply(itemSeqs),
                (a, b) -> ShareSummaryRules.togetherInPeriod(a, b, period).isPresent());

        Map<Long, MedicationRef> byId = new LinkedHashMap<>();
        refs.forEach(r -> byId.put(r.medicationId(), r));

        // 같은 약 쌍에 DUR 번호만 다르고 사유가 같은 항목은 한 건으로 합친다 (병용금기 목록과 같은 기준)
        Map<String, Finding> collapsed = new LinkedHashMap<>();
        for (Finding f : findings) {
            collapsed.putIfAbsent(f.medicationAId() + "|" + f.medicationBId() + "|" + InteractionTexts.reason(f.reason()), f);
        }

        List<ShareSummaryResponse.InteractionItem> items = new ArrayList<>();
        for (Finding f : collapsed.values()) {
            MedicationRef a = byId.get(f.medicationAId());
            MedicationRef b = byId.get(f.medicationBId());
            ShareSummaryRules.DateRange together = ShareSummaryRules.togetherInPeriod(a, b, period).orElseThrow();
            items.add(new ShareSummaryResponse.InteractionItem(a.name(), b.name(), together.from(), together.to(),
                    InteractionTexts.reason(f.reason()), InteractionTexts.source(f.sourceName()), datasetBaseDate.get()));
        }
        return new ShareSummaryResponse.InteractionSection(items, unchecked);
    }
}