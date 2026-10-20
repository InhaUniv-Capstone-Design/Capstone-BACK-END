package com.gamcho.yakyeon.domain.interaction.service;

import com.gamcho.yakyeon.domain.drug.entity.Drug;
import com.gamcho.yakyeon.domain.interaction.logic.MedicationRef;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 병용금기 검사 대상 약을 모은다: 삭제되지 않았고 복용 기간이 끝나지 않은 약 (복용 시작 전인 약도 포함).
 * 약을 저장하는 쪽과 조회하는 쪽이 같은 기준을 쓰도록 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class ActiveMedicationLoader {

    private final PatientMedicationRepository patientMedicationRepository;

    public List<MedicationRef> load(Long patientId, LocalDate today) {
        List<MedicationRef> result = new ArrayList<>();
        for (PatientMedication m : patientMedicationRepository.findAliveByPatientId(patientId)) {
            if (m.statusOn(today) == MedicationStatus.ENDED) {
                continue;
            }
            Drug drug = m.getDrug();
            result.add(new MedicationRef(
                    m.getMedicationId(), m.getInputName(),
                    drug == null ? null : drug.getItemSeq(),
                    m.getStartDate(), m.getEndDate()));
        }
        result.sort(Comparator.comparingLong(MedicationRef::medicationId));
        return result;
    }
}