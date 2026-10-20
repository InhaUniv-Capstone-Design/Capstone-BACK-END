package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.drug.entity.Drug;
import com.gamcho.yakyeon.domain.interaction.entity.InteractionCheck;
import com.gamcho.yakyeon.domain.interaction.service.InteractionService;
import com.gamcho.yakyeon.domain.medication.dto.MedicationItem;
import com.gamcho.yakyeon.domain.medication.dto.MedicationResponse;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.service.MedicationInputValidator.ValidatedMedication;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private static final String RESOURCE_TYPE = "patient_medication";

    /**
     * 약 목록의 정렬 기준: 복용 중 → 복용 예정 → 복용 종료. 복용 종료 안에서는 최근에 끝난 약이 먼저, 나머지는 등록순.
     * 병원별 조회처럼 약을 다른 모양으로 묶어서 보여줄 때도 같은 순서를 쓰도록 상수로 둔다.
     */
    public static final Comparator<MedicationResponse> LIST_ORDER =
            Comparator.<MedicationResponse>comparingInt(r -> order(r.status()))
                    .thenComparing((a, b) -> a.status() == MedicationStatus.ENDED
                            ? b.endDate().compareTo(a.endDate()) : 0)
                    .thenComparing(MedicationResponse::medicationId);

    private final PatientAccessGuard patientAccessGuard;
    private final PatientMedicationRepository patientMedicationRepository;
    private final MedicationInputValidator medicationInputValidator;
    private final InteractionService interactionService;

    /**
     * 복약자의 약 목록 (삭제된 약 제외).
     *  - date: 복용 상태를 계산할 기준일 (생략하면 한국 기준 오늘). 예) 특정 날짜에 복용 중인 약을 보려면 status=ACTIVE&date=...
     *  - status: 이 상태의 약만 (생략하면 전부)
     * 정렬: 복용 중 → 복용 예정 → 복용 종료. 복용 종료 안에서는 최근에 끝난 약이 먼저, 나머지는 등록순.
     */
    @Transactional(readOnly = true)
    public List<MedicationResponse> list(Long userId, Long patientId, MedicationStatus statusFilter, LocalDate date) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);

        LocalDate reference = date != null ? date : ApiTime.today();
        return patientMedicationRepository.findAliveByPatientId(patientId).stream()
                .map(m -> MedicationResponse.from(m, reference))
                .filter(r -> statusFilter == null || r.status() == statusFilter)
                .sorted(LIST_ORDER)
                .toList();
    }

    /**
     * 약 한 건 수정. 등록 때와 같은 형식으로 약의 전체 내용을 다시 받는다 (일부 필드만 고치는 방식이 아님).
     * start_date를 생략하면 기존 시작일을 유지한다.
     *
     * 일반 약 ↔ 필요 시 복용 약 전환은 막는다. 이 전환은 복용 시기를 지우고 is_prn을 바꾸는 순서가 DB 트리거 때문에
     * 정해져 있는데, 한 번의 저장에서는 Hibernate가 그 순서를 보장하지 못한다. 필요해지면 단계별로 flush하는
     * 별도 기능으로 만들고, 그 전까지는 삭제 후 다시 등록하도록 안내한다.
     */
    @Transactional
    public MedicationResponse update(Long userId, Long medicationId, MedicationItem item) {
        PatientMedication medication = findAlive(medicationId);
        authorize(userId, medication, AccessAction.UPDATE);

        boolean newPrn = Boolean.TRUE.equals(item.getPrn());
        if (newPrn != medication.isPrn()) {
            throw MedicationInputValidator.invalid(
                    "필요 시 복용 약과 일반 약 사이의 전환은 수정할 수 없습니다. 삭제한 뒤 다시 등록해주세요.");
        }

        Map<Long, Drug> drugs = medicationInputValidator.loadDrugs(List.of(item));
        ValidatedMedication v = medicationInputValidator.validate("", item, drugs);

        medication.update(v.drug(), v.inputName(), item.getDosePerIntake(), v.prn(), v.timesPerDay(),
                item.getMealRelation(), item.getMealOffsetMin(), item.getTotalDays(),
                item.getStartDate() != null ? item.getStartDate() : medication.getStartDate(),
                v.slots());

        // 약이 바뀌었으므로 이 복약자의 병용금기를 다시 검사해서 저장한다
        interactionService.recheck(medication.getPatient().getPatientId(), InteractionCheck.TriggerType.MED_UPDATE);

        return MedicationResponse.from(medication, ApiTime.today());
    }

    /** 약 삭제 (논리 삭제: deleted_at 표시). 이미 삭제된 약은 없는 것으로 취급해 404 */
    @Transactional
    public void delete(Long userId, Long medicationId) {
        PatientMedication medication = findAlive(medicationId);
        authorize(userId, medication, AccessAction.DELETE);

        medication.softDelete();

        // 약이 삭제됐으므로 이 복약자의 병용금기를 다시 검사해서 저장한다 (삭제한 약이 걸린 결과가 빠진다)
        interactionService.recheck(medication.getPatient().getPatientId(), InteractionCheck.TriggerType.MED_DELETE);
        // TODO(⑥ 스케줄): 아직 판정 전(PENDING)인 이 약의 예정 복용을 삭제한다 (이미 판정된 기록은 보관)
        // TODO(⑦ 약통): 이 약이 배정된 칸 배정을 종료(ENDED)한다
    }

    private PatientMedication findAlive(Long medicationId) {
        return patientMedicationRepository.findByMedicationIdAndDeletedAtIsNull(medicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICATION_NOT_FOUND));
    }

    /**
     * 약 번호만으로 약을 가리키는 경로의 접근 확인. 약을 찾은 뒤 그 약의 복약자로 접근 가드를 부른다.
     *
     * 가드는 "복약자와 연동이 없는 사람"에게 PATIENT_NOT_FOUND를 주는데, 약이 없을 때의 MEDICATION_NOT_FOUND와
     * 코드가 다르면 응답 코드만으로 "이 번호의 약이 존재한다"는 사실이 새 나간다. 그래서 같은 코드로 통일한다.
     * (연동은 있는데 권한이 모자란 경우의 403은 그대로 둔다 - 이미 그 복약자의 존재를 아는 사람이다.)
     */
    private void authorize(Long userId, PatientMedication medication, AccessAction action) {
        try {
            patientAccessGuard.check(userId, medication.getPatient().getPatientId(), action,
                    RESOURCE_TYPE, medication.getMedicationId());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PATIENT_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MEDICATION_NOT_FOUND);
            }
            throw e;
        }
    }

    private static int order(MedicationStatus status) {
        return switch (status) {
            case ACTIVE -> 0;
            case UPCOMING -> 1;
            case ENDED -> 2;
        };
    }
}