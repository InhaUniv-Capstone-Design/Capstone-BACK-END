package com.gamcho.yakyeon.domain.medication.repository;

import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 삭제는 deleted_at 표시(논리 삭제)라서, 목록·조회를 만들 때는 반드시 deleted_at이 비어 있는 행만 대상으로 해야 한다.
 */
public interface PatientMedicationRepository extends JpaRepository<PatientMedication, Long> {
}