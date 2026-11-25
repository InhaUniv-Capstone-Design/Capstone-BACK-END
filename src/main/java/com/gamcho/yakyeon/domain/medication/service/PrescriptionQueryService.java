package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.medication.dto.HospitalGroupResponse;
import com.gamcho.yakyeon.domain.medication.dto.MedicationResponse;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionResponse;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 처방 단위 조회와 병원별 통합 조회 (FR-LIST-006).
 *
 * 삭제되지 않은 약을 한 번에 가져와서 메모리에서 묶는다. 약 조회 쿼리가 처방·약물 정보를 함께 가져오므로
 * 처방을 따로 조회하지 않는다. 약이 하나도 남지 않는 처방(모두 삭제됐거나 상태 필터에서 제외됨)은 결과에서 빠진다.
 * 약의 정렬 기준과 상태 계산은 약 목록 조회와 같다 (MedicationService.LIST_ORDER).
 */
@Service
@RequiredArgsConstructor
public class PrescriptionQueryService {

    private static final String RESOURCE_TYPE = "prescription";

    private final PatientAccessGuard patientAccessGuard;
    private final PatientMedicationRepository patientMedicationRepository;

    /** 처방 단위 목록. 최근 처방일이 먼저이고, 처방일이 없는 처방은 마지막이다 */
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> listPrescriptions(Long userId, Long patientId,
                                                        MedicationStatus statusFilter, LocalDate date) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);
        return buildPrescriptions(patientId, statusFilter, date);
    }

    /**
     * 병원별 목록. 같은 병원의 여러 처방에 흩어진 약을 한 묶음으로 모은다.
     * 병원은 앞뒤·중복 공백을 정리한 이름이 같으면 같은 병원으로 본다 (비슷한 이름까지 합치지는 않는다).
     * 묶음은 그 병원의 가장 최근 처방일이 먼저이고, 병원명을 모르는 묶음은 마지막이다.
     */
    @Transactional(readOnly = true)
    public List<HospitalGroupResponse> listByHospital(Long userId, Long patientId,
                                                      MedicationStatus statusFilter, LocalDate date) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);

        // 처방 단위 결과(이미 최근 처방일 순)를 병원 이름으로 다시 묶는다. LinkedHashMap이라 병원의 등장 순서가 그대로 유지된다
        Map<String, List<PrescriptionResponse>> byHospital = new LinkedHashMap<>();
        for (PrescriptionResponse rx : buildPrescriptions(patientId, statusFilter, date)) {
            byHospital.computeIfAbsent(hospitalKey(rx.hospitalName()), k -> new ArrayList<>()).add(rx);
        }

        List<HospitalGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<String, List<PrescriptionResponse>> e : byHospital.entrySet()) {
            List<MedicationResponse> meds = e.getValue().stream()
                    .flatMap(rx -> rx.medications().stream())
                    .sorted(MedicationService.LIST_ORDER)
                    .toList();
            groups.add(new HospitalGroupResponse(e.getKey(), meds));
        }
        // 병원명을 모르는 묶음(null)을 맨 뒤로. 나머지는 위에서 정해진 순서(최근 처방일 순)를 유지한다
        groups.sort(Comparator.comparing((HospitalGroupResponse g) -> g.hospitalName() == null));
        return groups;
    }

    /** 처방 단위 결과를 만든다 (처방일 최신순, 처방일 없음은 마지막, 같으면 번호가 큰 것이 먼저) */
    private List<PrescriptionResponse> buildPrescriptions(Long patientId, MedicationStatus statusFilter, LocalDate date) {
        LocalDate reference = date != null ? date : ApiTime.today();

        // 처방별로 삭제되지 않은 약을 모은다. 처방 없이 등록된 약은 null 키 하나로 모인다
        Map<Long, List<PatientMedication>> byPrescription = new LinkedHashMap<>();
        for (PatientMedication m : patientMedicationRepository.findAliveByPatientId(patientId)) {
            Long key = m.getPrescription() == null ? null : m.getPrescription().getPrescriptionId();
            byPrescription.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        List<PrescriptionResponse> result = new ArrayList<>();
        for (List<PatientMedication> meds : byPrescription.values()) {
            List<MedicationResponse> responses = meds.stream()
                    .map(m -> MedicationResponse.from(m, reference))
                    .filter(r -> statusFilter == null || r.status() == statusFilter)
                    .sorted(MedicationService.LIST_ORDER)
                    .toList();
            if (responses.isEmpty()) {
                continue;
            }
            Prescription rx = meds.get(0).getPrescription();
            result.add(new PrescriptionResponse(
                    rx == null ? null : rx.getPrescriptionId(),
                    rx == null ? null : rx.getHospitalName(),
                    rx == null ? null : rx.getPharmacyName(),
                    rx == null ? null : rx.getPrescribedDate(),
                    responses));
        }

        result.sort(Comparator
                .comparing(PrescriptionResponse::prescribedDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
                .thenComparing(PrescriptionResponse::prescriptionId, Comparator.nullsLast(Comparator.<Long>reverseOrder())));
        return result;
    }

    /** 병원 비교용 키: 앞뒤 공백을 지우고 연속 공백을 하나로. 비어 있으면 null(병원 모름) */
    private static String hospitalKey(String hospitalName) {
        if (hospitalName == null) {
            return null;
        }
        String normalized = hospitalName.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
}