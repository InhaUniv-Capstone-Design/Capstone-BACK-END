package com.gamcho.yakyeon.domain.dose.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 복용 예정 한 건 (scheduled_dose) = 처방(약봉지) 하나의 한 시간대 한 번 복용.
 * 약마다가 아니라 약봉지 단위다 - 그 시간대에 먹는 약들은 예정 하나에 묶이고, 약 목록은 DB 뷰(v_scheduled_dose_item)로 본다.
 * 같은 처방·같은 예정 시각의 예정은 하나뿐이다 (DB 유니크).
 *
 * 예정은 DoseScheduleService가 만들고 지운다. 이미 판정된(PENDING이 아닌) 예정은 복용 기록이라
 * 절대 지우거나 바꾸지 않는다 - DB 트리거도 판정 후 변경을 막고, 정정은 correct_dose()로만 한다.
 */
@Entity
@Table(name = "scheduled_dose")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledDose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scheduled_dose_id")
    private Long scheduledDoseId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    /** 이 예정을 만들 때 쓴 시각 설정의 버전. 시각이 바뀌어도 예전 예정은 예전 버전을 계속 가리킨다 */
    @Column(name = "schedule_setting_id", nullable = false)
    private Long scheduleSettingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "timing_slot", nullable = false, length = 10)
    private TimingSlot timingSlot;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    /** 이 시각까지 복용하면 정상, 이후는 지연 */
    @Column(name = "normal_until", nullable = false)
    private OffsetDateTime normalUntil;

    /** 이 시각이 지나면 누락으로 확정 */
    @Column(name = "close_at", nullable = false)
    private OffsetDateTime closeAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DoseStatus status;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_source", length = 10)
    private DecisionSource decisionSource;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    /** 실제로 복용한 시각. 정상·지연일 때만 값이 있다 */
    @Column(name = "taken_at")
    private OffsetDateTime takenAt;
}