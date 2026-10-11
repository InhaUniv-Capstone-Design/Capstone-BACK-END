package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccess;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.drug.entity.Drug;
import com.gamcho.yakyeon.domain.medication.dto.MedicationItem;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionCreateRequest;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionCreateResponse;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import com.gamcho.yakyeon.domain.medication.service.MedicationInputValidator.ValidatedMedication;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PatientAccessGuard patientAccessGuard;
    private final AppUserRepository appUserRepository;
    private final MedicationInputValidator medicationInputValidator;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientMedicationRepository patientMedicationRepository;

    /**
     * 처방 등록. 처방 1건과 그 안의 모든 약, 약별 복용 시기를 한 트랜잭션으로 저장한다 -
     * 하나라도 실패하면 전부 취소된다 (일부 약만 등록된 처방이 남지 않게).
     *
     * 접근 권한: 복약자 본인이거나, 활성 연동이 있고 쓰기 권한(READ_WRITE)이 있는 보호자.
     * 읽기 전용 보호자는 403이다.
     */
    @Transactional
    public PrescriptionCreateResponse create(Long userId, Long patientId, PrescriptionCreateRequest request) {
        // 가드가 먼저다: 권한 없는 사람에게 입력 검증 결과(어떤 약이 DB에 있는지 등)를 알려주지 않는다
        PatientAccess access = patientAccessGuard.check(
                userId, patientId, AccessAction.CREATE, "prescription", null);

        List<MedicationItem> items = request.getMedications();
        Map<Long, Drug> drugs = medicationInputValidator.loadDrugs(items);

        // 모든 약을 먼저 검사하고 나서 저장한다
        List<ValidatedMedication> validated = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            validated.add(medicationInputValidator.validate((i + 1) + "번째 약: ", items.get(i), drugs));
        }

        AppUser creator = appUserRepository.getReferenceById(userId);

        Prescription prescription = prescriptionRepository.save(Prescription.builder()
                .patient(access.patient())
                .source(request.getSource() != null ? request.getSource() : Prescription.Source.MANUAL)
                .hospitalName(blankToNull(request.getHospitalName()))
                .pharmacyName(blankToNull(request.getPharmacyName()))
                .prescribedDate(request.getPrescribedDate())
                .createdBy(creator)
                .build());

        List<Long> medicationIds = new ArrayList<>();
        for (ValidatedMedication v : validated) {
            PatientMedication saved = patientMedicationRepository.save(PatientMedication.builder()
                    .patient(access.patient())
                    .prescription(prescription)
                    .drug(v.drug())
                    .inputName(v.inputName())
                    .dosePerIntake(v.item().getDosePerIntake())
                    .prn(v.prn())
                    .timesPerDay(v.timesPerDay())
                    .mealRelation(v.item().getMealRelation())
                    .mealOffsetMin(v.item().getMealOffsetMin())
                    .totalDays(v.item().getTotalDays())
                    .startDate(v.item().getStartDate() != null ? v.item().getStartDate() : ApiTime.today())
                    .createdBy(creator)
                    .timingSlots(v.slots())
                    .build());
            medicationIds.add(saved.getMedicationId());
        }

        // TODO(⑤ 병용금기): 약이 추가됐으므로 이 복약자의 병용금기 재검사를 여기서 호출한다 (trigger_type = MED_ADD)

        return new PrescriptionCreateResponse(prescription.getPrescriptionId(), medicationIds);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}