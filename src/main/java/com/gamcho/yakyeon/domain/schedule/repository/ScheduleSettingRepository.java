package com.gamcho.yakyeon.domain.schedule.repository;

import com.gamcho.yakyeon.domain.schedule.entity.ScheduleSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleSettingRepository extends JpaRepository<ScheduleSetting, Long> {

    /** 처방의 현재 유효한 설정 (닫히지 않은 행) */
    List<ScheduleSetting> findByPrescriptionIdAndEffectiveToIsNull(Long prescriptionId);
}