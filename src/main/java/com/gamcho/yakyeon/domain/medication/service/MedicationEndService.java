package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.interaction.entity.InteractionCheck;
import com.gamcho.yakyeon.domain.interaction.service.InteractionService;
import com.gamcho.yakyeon.domain.medication.dto.MedicationResponse;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionEndResponse;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 복용 종료 (약 한 건 / 처방(약봉지) 전체). 삭제와 다르다 - 삭제는 잘못 등록한 약을 없애는 것이고,
 * 종료는 복용을 마친 약을 "종료됨"으로 남기는 것이다. 지난 복용 기록은 그대로 보관된다.
 *
 * 종료하면 (1) 이 시각 이후의 예정에서 약이 빠지고 (2) 처방의 약이 모두 끝났거나 처방 전체를 종료했으면
 * 배정된 약통 칸이 비워지고 (3) 병용금기가 다시 계산돼서 끝난 약이 목록에서 빠진다.
 * 복약자의 건강정보를 바꾸는 일이라 수정 권한이 있어야 한다 (본인 또는 읽기·쓰기 권한 보호자).
 */
@Service
@RequiredArgsConstructor
public class MedicationEndService {

    private static final String MEDICATION_RESOURCE = "medication";
    private static final String PRESCRIPTION_RESOURCE = "prescription";

    private final PatientAccessGuard patientAccessGuard;
    private final PatientMedicationRepository patientMedicationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final InteractionService interactionService;
    private final JdbcTemplate jdbcTemplate;

    /** 약 한 건 종료. 지금 복용 중인 약만 종료할 수 있다 (시작 전인 약은 삭제, 이미 끝난 약은 거절) */
    @Transactional
    public MedicationResponse endMedication(Long userId, Long medicationId) {
        PatientMedication medication = patientMedicationRepository.findByMedicationIdAndDeletedAtIsNull(medicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICATION_NOT_FOUND));
        Long patientId = medication.getPatient().getPatientId();
        authorize(userId, patientId, MEDICATION_RESOURCE, medicationId, ErrorCode.MEDICATION_NOT_FOUND);

        LocalDate today = ApiTime.today();
        MedicationStatus status = medication.statusOn(today);
        if (status == MedicationStatus.ENDED) {
            throw new BusinessException(ErrorCode.MEDICATION_ALREADY_ENDED);
        }
        if (status == MedicationStatus.UPCOMING) {
            throw new BusinessException(ErrorCode.MEDICATION_NOT_STARTED);
        }

        medication.end(OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        afterEnded(patientId);
        return MedicationResponse.from(medication, today);
    }

    /** 처방(약봉지) 전체 종료. 아직 끝나지 않은 약이 하나라도 있어야 한다 (시작 전인 약도 함께 종료된다) */
    @Transactional
    public PrescriptionEndResponse endPrescription(Long userId, Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        Long patientId = prescription.getPatient().getPatientId();
        authorize(userId, patientId, PRESCRIPTION_RESOURCE, prescriptionId, ErrorCode.PRESCRIPTION_NOT_FOUND);

        if (prescription.isEnded()) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_ENDED);
        }
        LocalDate today = ApiTime.today();
        List<PatientMedication> stillOpen = patientMedicationRepository.findAliveByPatientId(patientId).stream()
                .filter(m -> m.getPrescription() != null
                        && prescriptionId.equals(m.getPrescription().getPrescriptionId()))
                .filter(m -> m.statusOn(today) != MedicationStatus.ENDED)
                .toList();
        if (stillOpen.isEmpty()) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_ENDED);
        }

        prescription.end(OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        afterEnded(patientId);
        return new PrescriptionEndResponse(prescriptionId, prescription.getEndedAt(), stillOpen.size());
    }

    /**
     * 종료 뒤처리. 먼저 변경을 DB에 내보내야(flush) DB 함수가 방금 바뀐 종료 시각을 본다.
     * 칸 비우기 함수는 "끝난 처방의 칸"을 모두 정리하는 멱등 함수라 여러 번 불러도 안전하다
     * (스케줄러가 하루 한 번 부르는 같은 함수다).
     */
    private void afterEnded(Long patientId) {
        patientMedicationRepository.flush();
        jdbcTemplate.queryForObject("SELECT release_ended_compartments(?)", Long.class,
                OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        interactionService.recheck(patientId, InteractionCheck.TriggerType.MED_UPDATE);
    }

    /**
     * 번호만으로 약·처방을 가리키는 경로의 접근 확인. 연동이 없는 사람에게는 "없는 번호"와 같은 오류를 줘서
     * 응답 코드만으로 그 번호가 존재한다는 사실이 새 나가지 않게 한다 (MedicationService와 같은 방식).
     * 연동은 있는데 권한이 모자란 경우의 403은 그대로 둔다 - 이미 그 복약자의 존재를 아는 사람이다.
     */
    private void authorize(Long userId, Long patientId, String resourceType, Long resourceId, ErrorCode notFound) {
        try {
            patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, resourceType, resourceId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PATIENT_NOT_FOUND) {
                throw new BusinessException(notFound);
            }
            throw e;
        }
    }
}