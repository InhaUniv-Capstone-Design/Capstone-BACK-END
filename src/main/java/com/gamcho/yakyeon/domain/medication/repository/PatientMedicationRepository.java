package com.gamcho.yakyeon.domain.medication.repository;

import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 삭제는 deleted_at 표시(논리 삭제)라서, 조회는 항상 deleted_at이 비어 있는 행만 대상으로 한다.
 */
public interface PatientMedicationRepository extends JpaRepository<PatientMedication, Long> {

    /**
     * 복약자의 삭제되지 않은 약 전체. 목록 화면이 약마다 복용 시기·처방(병원)·약물 정보를 함께 보여주므로
     * 한 번에 가져온다 (약이 N개일 때 추가 조회가 N번씩 나가는 것을 막는다).
     * 복용 시기(컬렉션)를 fetch join하므로 같은 약이 시기 수만큼 중복되지 않게 distinct를 쓴다.
     */
    @Query("select distinct m from PatientMedication m "
            + "left join fetch m.timings "
            + "left join fetch m.prescription "
            + "left join fetch m.drug "
            + "where m.patient.patientId = :patientId and m.deletedAt is null")
    List<PatientMedication> findAliveByPatientId(@Param("patientId") Long patientId);

    /** 삭제되지 않은 약 한 건. 이미 삭제된 약은 없는 것으로 취급한다 */
    Optional<PatientMedication> findByMedicationIdAndDeletedAtIsNull(Long medicationId);
}