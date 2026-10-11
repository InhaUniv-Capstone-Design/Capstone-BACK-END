package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccess;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.drug.entity.Drug;
import com.gamcho.yakyeon.domain.drug.repository.DrugRepository;
import com.gamcho.yakyeon.domain.medication.dto.MedicationItem;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionCreateRequest;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionCreateResponse;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PatientAccessGuard patientAccessGuard;
    private final AppUserRepository appUserRepository;
    private final DrugRepository drugRepository;
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
        Map<Long, Drug> drugs = loadDrugs(items);

        // 모든 약을 먼저 검사하고 나서 저장한다
        List<Validated> validated = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            validated.add(validate(i + 1, items.get(i), drugs));
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
        for (Validated v : validated) {
            PatientMedication saved = patientMedicationRepository.save(PatientMedication.builder()
                    .patient(access.patient())
                    .prescription(prescription)
                    .drug(v.drug)
                    .inputName(v.inputName)
                    .dosePerIntake(v.item.getDosePerIntake())
                    .prn(v.prn)
                    .timesPerDay(v.timesPerDay)
                    .mealRelation(v.item.getMealRelation())
                    .mealOffsetMin(v.item.getMealOffsetMin())
                    .totalDays(v.item.getTotalDays())
                    .startDate(v.item.getStartDate() != null ? v.item.getStartDate() : LocalDate.now())
                    .createdBy(creator)
                    .timingSlots(v.slots)
                    .build());
            medicationIds.add(saved.getMedicationId());
        }

        // TODO(⑤ 병용금기): 약이 추가됐으므로 이 복약자의 병용금기 재검사를 여기서 호출한다 (trigger_type = MED_ADD)

        return new PrescriptionCreateResponse(prescription.getPrescriptionId(), medicationIds);
    }

    /** 요청에 담긴 drug_id를 한 번에 조회한다. DB에 없는 drug_id가 하나라도 있으면 404 */
    private Map<Long, Drug> loadDrugs(List<MedicationItem> items) {
        Set<Long> ids = items.stream().map(MedicationItem::getDrugId)
                .filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Drug> found = drugRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Drug::getDrugId, Function.identity()));
        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw new BusinessException(ErrorCode.DRUG_NOT_FOUND,
                        "존재하지 않는 약물입니다. (drug_id: " + id + ")");
            }
        }
        return found;
    }

    /**
     * 약 한 건의 규칙 검사. DB 제약과 트리거가 막는 조합을 500이 아니라 400으로, 어느 약인지 알려주며 막는다.
     * position은 요청 안에서의 순서(1부터)이다.
     */
    private Validated validate(int position, MedicationItem item, Map<Long, Drug> drugs) {
        String where = position + "번째 약: ";
        boolean prn = Boolean.TRUE.equals(item.getPrn());
        List<TimingSlot> slots = item.getTimingSlots() == null ? List.of() : item.getTimingSlots();

        // 약 확정과 이름
        Drug drug = item.getDrugId() == null ? null : drugs.get(item.getDrugId());
        String inputName = blankToNull(item.getInputName());
        if (inputName == null && drug != null) {
            inputName = drug.getProductName();
        }
        if (inputName == null) {
            throw invalid(where + "약 이름(input_name)이 필요합니다. (약물을 선택하지 않은 경우 직접 입력한 이름이 필요합니다.)");
        }

        // 복용 시기 중복
        if (new HashSet<>(slots).size() != slots.size()) {
            throw invalid(where + "복용 시기(timing_slots)가 중복되었습니다.");
        }

        Short timesPerDay;
        if (prn) {
            // 필요 시 복용: 복용 횟수·시기를 가질 수 없다 (DB CHECK ck_med_prn_times + 트리거)
            if (item.getTimesPerDay() != null || !slots.isEmpty()) {
                throw invalid(where + "필요 시 복용 약은 복용 횟수와 복용 시기를 가질 수 없습니다.");
            }
            timesPerDay = null;
        } else {
            if (slots.isEmpty()) {
                throw invalid(where + "복용 시기(timing_slots)를 1개 이상 선택해주세요. (필요 시 복용이면 is_prn을 true로 보내주세요.)");
            }
            if (item.getTimesPerDay() != null && item.getTimesPerDay() != slots.size()) {
                throw invalid(where + "하루 복용 횟수(" + item.getTimesPerDay()
                        + "회)와 복용 시기 개수(" + slots.size() + "개)가 다릅니다.");
            }
            timesPerDay = (short) slots.size();
        }

        // 식사 전후 시간은 식사 관계와 함께만 (DB CHECK ck_med_meal_offset)
        if (item.getMealOffsetMin() != null && item.getMealRelation() == null) {
            throw invalid(where + "식사 전후 시간(meal_offset_min)은 식사 관계(meal_relation)와 함께 보내주세요.");
        }

        return new Validated(item, drug, inputName, prn, timesPerDay, slots);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_MEDICATION, message);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 검사를 통과한 약 한 건 (저장할 값을 정리해 둔 것) */
    private record Validated(MedicationItem item, Drug drug, String inputName, boolean prn,
                             Short timesPerDay, List<TimingSlot> slots) {
    }
}