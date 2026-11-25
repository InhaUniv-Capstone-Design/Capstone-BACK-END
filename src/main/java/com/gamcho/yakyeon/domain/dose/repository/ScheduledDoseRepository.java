package com.gamcho.yakyeon.domain.dose.repository;

import com.gamcho.yakyeon.domain.dose.entity.DoseStatus;
import com.gamcho.yakyeon.domain.dose.entity.ScheduledDose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface ScheduledDoseRepository extends JpaRepository<ScheduledDose, Long> {

    /** 처방의 예정 중 기준 시각 이후의 것 (일정 점검용) */
    List<ScheduledDose> findByPrescriptionIdAndScheduledAtGreaterThanEqual(Long prescriptionId, OffsetDateTime from);

    /**
     * 번호로 예정을 지운다. 판정 전(PENDING)인 것만 지운다 - 번호 목록에 판정된 예정이 섞여 있어도
     * 복용 기록은 절대 지워지지 않게 쿼리 자체에 조건을 둔다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ScheduledDose d where d.scheduledDoseId in :ids and d.status = :status")
    int deleteByIdsAndStatus(@Param("ids") Collection<Long> ids, @Param("status") DoseStatus status);

    default int deletePendingByIds(Collection<Long> ids) {
        return deleteByIdsAndStatus(ids, DoseStatus.PENDING);
    }

    /** 현재 유효한 복용 시각이 있는 처방 (일정 점검 대상) */
    @Query("select distinct s.prescriptionId from ScheduleSetting s where s.effectiveTo is null")
    List<Long> currentSettingPrescriptionIds();
}