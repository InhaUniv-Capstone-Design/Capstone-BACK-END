package com.gamcho.yakyeon.domain.interaction.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionEdgeDetailResponse;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionGraphResponse;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionItemResponse;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionSummaryResponse;
import com.gamcho.yakyeon.domain.interaction.entity.InteractionCheck;
import com.gamcho.yakyeon.domain.interaction.entity.InteractionFinding;
import com.gamcho.yakyeon.domain.interaction.logic.Finding;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionGraphs;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionMatcher;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionTexts;
import com.gamcho.yakyeon.domain.interaction.logic.MedicationRef;
import com.gamcho.yakyeon.domain.interaction.repository.DurTabooLookup;
import com.gamcho.yakyeon.domain.interaction.repository.InteractionCheckRepository;
import com.gamcho.yakyeon.domain.interaction.repository.InteractionFindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 병용금기 조회 (목록·요약·그래프·엣지 상세). 복약자의 건강정보라서 접근 가드를 거친다 (조회 권한).
 *
 * 화면에는 가장 최근의 성공 검사 결과를 보여주되, 그 뒤에 약이 끝났거나 삭제된 경우는 오늘 기준으로 걸러낸다.
 * 아직 검사 기록이 없는 복약자(이 기능 이전에 등록한 약 등)는 지금 약으로 바로 계산해서 보여주고 저장은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InteractionQueryService {

    private static final String RESOURCE_TYPE = "interaction";

    private final PatientAccessGuard patientAccessGuard;
    private final ActiveMedicationLoader activeMedicationLoader;
    private final InteractionCheckRepository interactionCheckRepository;
    private final InteractionFindingRepository interactionFindingRepository;
    private final DurTabooLookup durTabooLookup;

    @Transactional(readOnly = true)
    public List<InteractionItemResponse> list(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);
        Snapshot s = snapshot(patientId);
        return s.shown().stream()
                .map(sh -> new InteractionItemResponse(
                        sh.finding().medicationAId(), nameOf(s, sh.finding().medicationAId()),
                        sh.finding().medicationBId(), nameOf(s, sh.finding().medicationBId()),
                        sh.finding().durSeq(),
                        InteractionTexts.reason(sh.finding().reason()),
                        InteractionTexts.source(sh.finding().sourceName()),
                        sh.sourceBaseDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InteractionSummaryResponse summary(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);
        Snapshot s = snapshot(patientId);
        LocalDate today = ApiTime.today();
        int total = (int) s.meds().stream().filter(m -> m.activeOn(today)).count();
        int unchecked = (int) s.meds().stream().filter(m -> !m.checkable()).count();
        return new InteractionSummaryResponse(total, s.shown().size(), unchecked);
    }

    @Transactional(readOnly = true)
    public InteractionGraphResponse graph(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);
        Snapshot s = snapshot(patientId);

        List<InteractionGraphResponse.Node> nodes = s.meds().stream()
                .map(m -> new InteractionGraphResponse.Node(m.medicationId(), m.name(), m.checkable()))
                .toList();
        List<InteractionGraphResponse.Edge> edges = s.shown().stream()
                .map(sh -> new InteractionGraphResponse.Edge(
                        sh.finding().medicationAId(), sh.finding().medicationBId(), sh.finding().durSeq(),
                        InteractionTexts.reason(sh.finding().reason()),
                        InteractionTexts.source(sh.finding().sourceName())))
                .toList();
        List<long[]> edgePairs = s.shown().stream()
                .map(sh -> new long[]{sh.finding().medicationAId(), sh.finding().medicationBId()})
                .toList();
        return new InteractionGraphResponse(nodes, edges, InteractionGraphs.connectedGroups(edgePairs));
    }

    /**
     * 엣지(연결선) 상세. DUR 규칙 자체는 공개된 참고 정보이고 복약자 데이터가 아니라서 복약자별 가드는 거치지 않는다
     * (로그인은 필요하다). 사유가 비어 있는 규칙은 안내 문구로 바꿔서 내려준다.
     */
    @Transactional(readOnly = true)
    public InteractionEdgeDetailResponse edgeDetail(String durSeq) {
        if (durSeq == null || durSeq.isBlank() || durSeq.length() > 20) {
            throw new BusinessException(ErrorCode.INTERACTION_NOT_FOUND);
        }
        DurTabooLookup.RuleDetail rule = durTabooLookup.findRule(durSeq.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERACTION_NOT_FOUND));
        return new InteractionEdgeDetailResponse(
                InteractionTexts.reason(rule.reason()),
                InteractionTexts.source(rule.sourceName()),
                rule.baseDate(),
                InteractionTexts.PRECAUTION,
                InteractionTexts.DISCLAIMER);
    }

    // ------------------------------------------------------------------ 내부

    private record Shown(Finding finding, LocalDate sourceBaseDate) {
    }

    private record Snapshot(List<MedicationRef> meds, Map<Long, MedicationRef> medsById, List<Shown> shown) {
    }

    private Snapshot snapshot(Long patientId) {
        LocalDate today = ApiTime.today();
        List<MedicationRef> meds = activeMedicationLoader.load(patientId, today);
        Map<Long, MedicationRef> medsById = meds.stream()
                .collect(Collectors.toMap(MedicationRef::medicationId, Function.identity()));

        List<Shown> shown = new ArrayList<>();
        Optional<InteractionCheck> latest = interactionCheckRepository
                .findFirstByPatientIdAndStatusOrderByCheckedAtDescCheckIdDesc(patientId, InteractionCheck.Status.SUCCESS);
        if (latest.isPresent()) {
            for (InteractionFinding f : interactionFindingRepository.findByCheckId(latest.get().getCheckId())) {
                MedicationRef a = medsById.get(f.getMedicationAId());
                MedicationRef b = medsById.get(f.getMedicationBId());
                // 검사 뒤에 삭제됐거나 끝난 약, 복용 기간이 겹치지 않게 된 쌍은 오늘 기준으로 뺀다
                if (a == null || b == null || !InteractionMatcher.isRelevantPair(a, b, today)) {
                    continue;
                }
                shown.add(new Shown(new Finding(f.getMedicationAId(), f.getMedicationBId(), f.getDurSeq(),
                        f.getIngredientACode(), f.getIngredientBCode(), f.getSourceTypeCode(),
                        f.getReasonText(), f.getRemark(), f.getSourceName()), f.getSourceBaseDate()));
            }
        } else {
            Optional<DurTabooLookup.DatasetInfo> dataset = durTabooLookup.latestSuccessfulDataset();
            if (dataset.isPresent()) {
                List<String> itemSeqs = meds.stream()
                        .filter(MedicationRef::checkable).map(MedicationRef::itemSeq).distinct().toList();
                for (Finding f : InteractionMatcher.match(meds, durTabooLookup.findPairs(itemSeqs), today)) {
                    shown.add(new Shown(f, dataset.get().baseDate()));
                }
            }
        }

        shown.sort(Comparator.comparingLong((Shown s) -> s.finding().medicationAId())
                .thenComparingLong(s -> s.finding().medicationBId())
                .thenComparing(s -> s.finding().durSeq()));
        return new Snapshot(meds, medsById, collapseSameReason(shown));
    }

    /**
     * 같은 약 쌍에 DUR 번호만 다르고 사유가 같은 항목은 한 건으로 합친다 (화면에 같은 문장이 반복되는 것 방지).
     * 정렬된 순서에서 DUR 번호가 가장 앞선 것을 남긴다.
     */
    private static List<Shown> collapseSameReason(List<Shown> sorted) {
        Map<String, Shown> byKey = new LinkedHashMap<>();
        for (Shown s : sorted) {
            Finding f = s.finding();
            byKey.putIfAbsent(f.medicationAId() + "|" + f.medicationBId() + "|" + InteractionTexts.reason(f.reason()), s);
        }
        return new ArrayList<>(byKey.values());
    }

    private static String nameOf(Snapshot s, long medicationId) {
        MedicationRef m = s.medsById().get(medicationId);
        return m == null ? null : m.name();
    }
}