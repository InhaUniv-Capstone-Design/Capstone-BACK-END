package com.gamcho.yakyeon.domain.interaction.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.interaction.entity.InteractionCheck;
import com.gamcho.yakyeon.domain.interaction.entity.InteractionFinding;
import com.gamcho.yakyeon.domain.interaction.logic.Finding;
import com.gamcho.yakyeon.domain.interaction.logic.FoundPair;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionMatcher;
import com.gamcho.yakyeon.domain.interaction.logic.MedicationRef;
import com.gamcho.yakyeon.domain.interaction.logic.NewPairs;
import com.gamcho.yakyeon.domain.interaction.repository.DurTabooLookup;
import com.gamcho.yakyeon.domain.interaction.repository.InteractionCheckRepository;
import com.gamcho.yakyeon.domain.interaction.repository.InteractionFindingRepository;
import com.gamcho.yakyeon.domain.notification.service.NotificationCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 병용금기 재검사 (FR-ENGINE). 약이 추가·수정·삭제될 때 호출해서 복약자의 현재 약으로 검사 결과를 새로 저장한다.
 *
 * 반드시 약을 바꾼 트랜잭션 안에서 호출해야 한다 - 별도 트랜잭션이면 아직 저장되지 않은 변경을 보지 못한다.
 * 검사 결과 조회는 InteractionQueryService가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final ActiveMedicationLoader activeMedicationLoader;
    private final InteractionCheckRepository interactionCheckRepository;
    private final InteractionFindingRepository interactionFindingRepository;
    private final DurTabooLookup durTabooLookup;
    private final NotificationCreator notificationCreator;

    @Transactional
    public void recheck(Long patientId, InteractionCheck.TriggerType trigger) {
        Optional<DurTabooLookup.DatasetInfo> dataset = durTabooLookup.latestSuccessfulDataset();
        if (dataset.isEmpty()) {
            // DUR 데이터가 아직 적재되지 않았다. 약 등록 자체를 막지 않고 실패 기록만 남긴다.
            interactionCheckRepository.save(InteractionCheck.builder()
                    .patientId(patientId)
                    .triggerType(trigger)
                    .status(InteractionCheck.Status.FAILED)
                    .build());
            return;
        }

        LocalDate today = ApiTime.today();
        List<MedicationRef> meds = activeMedicationLoader.load(patientId, today);
        List<String> itemSeqs = meds.stream()
                .filter(MedicationRef::checkable)
                .map(MedicationRef::itemSeq)
                .distinct()
                .toList();
        List<FoundPair> pairs = durTabooLookup.findPairs(itemSeqs);
        List<Finding> findings = InteractionMatcher.match(meds, pairs, today);

        // 새로 생긴 쌍을 가리려고, 이번 검사를 저장하기 전에 직전 성공 검사의 쌍을 읽어 둔다
        Set<NewPairs.PairKey> previousPairs = interactionCheckRepository
                .findFirstByPatientIdAndStatusOrderByCheckedAtDescCheckIdDesc(patientId, InteractionCheck.Status.SUCCESS)
                .map(previous -> interactionFindingRepository.findByCheckId(previous.getCheckId()).stream()
                        .map(f -> NewPairs.keyOf(f.getMedicationAId(), f.getMedicationBId(), f.getDurSeq()))
                        .collect(Collectors.toSet()))
                .orElse(Set.of());

        InteractionCheck check = interactionCheckRepository.save(InteractionCheck.builder()
                .patientId(patientId)
                .triggerType(trigger)
                .status(InteractionCheck.Status.SUCCESS)
                .durDatasetId(dataset.get().datasetId())
                .build());

        interactionFindingRepository.saveAll(findings.stream()
                .map(f -> InteractionFinding.builder()
                        .checkId(check.getCheckId())
                        .medicationAId(f.medicationAId())
                        .medicationBId(f.medicationBId())
                        .durSeq(f.durSeq())
                        .ingredientACode(f.ingredientACode())
                        .ingredientBCode(f.ingredientBCode())
                        .sourceTypeCode(f.typeCode())
                        .reasonText(f.reason())
                        .remark(f.remark())
                        .sourceName(f.sourceName())
                        .sourceBaseDate(dataset.get().baseDate())
                        .build())
                .toList());

        // 새 등재 쌍이 있으면 복약자에게 알린다 (같은 쌍이 매번 나오는 것을 막으려고 직전 검사와 비교한다)
        List<NewPairs.PairKey> currentPairs = findings.stream()
                .map(f -> NewPairs.keyOf(f.medicationAId(), f.medicationBId(), f.durSeq()))
                .toList();
        if (!NewPairs.newPairs(previousPairs, currentPairs).isEmpty()) {
            interactionCheckRepository.flush(); // 알림이 가리킬 검사 행이 DB에 있어야 한다
            notificationCreator.onInteractionFound(patientId, check.getCheckId());
        }
    }
}