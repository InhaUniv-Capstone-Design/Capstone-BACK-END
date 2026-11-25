package com.gamcho.yakyeon.domain.schedule.entity;

import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 처방(약봉지)별·시간대별 복용 시각 (schedule_setting). 시각을 바꾸면 행을 고치지 않고
 * 기존 행을 닫고(effective_to) 새 행을 추가한다 - 이미 판정된 예정은 당시의 시각을 계속 가리켜야 하기 때문이다.
 * 현재 유효한 설정은 effective_to가 비어 있는 행이고, 처방·시간대마다 하나뿐이다 (DB 유니크 인덱스).
 */
@Entity
@Table(name = "schedule_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_setting_id")
    private Long scheduleSettingId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "timing_slot", nullable = false, length = 10)
    private TimingSlot timingSlot;

    @Column(name = "dose_time", nullable = false)
    private LocalTime doseTime;

    /** 예정 시각부터 이 시간(분) 안에 복용하면 정상 */
    @Column(name = "normal_window_min", nullable = false)
    private Short normalWindowMin;

    /** 예정 시각부터 이 시간(분)이 지나면 누락으로 확정 */
    @Column(name = "close_after_min", nullable = false)
    private Short closeAfterMin;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Builder
    public ScheduleSetting(Long patientId, Long prescriptionId, TimingSlot timingSlot, LocalTime doseTime,
                           Short normalWindowMin, Short closeAfterMin, OffsetDateTime effectiveFrom) {
        this.patientId = patientId;
        this.prescriptionId = prescriptionId;
        this.timingSlot = timingSlot;
        this.doseTime = doseTime;
        this.normalWindowMin = normalWindowMin;
        this.closeAfterMin = closeAfterMin;
        this.effectiveFrom = effectiveFrom;
    }

    /** 이 설정을 닫는다 (이후 예정부터는 새 설정이 적용된다). 이미 만들어진 예정은 이 행을 계속 가리킨다 */
    public void close(OffsetDateTime when) {
        this.effectiveTo = when;
    }
}