package com.gamcho.yakyeon.domain.medication.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약별 복용 시기 (medication_timing). 약 하나가 아침·점심·저녁 중 언제 복용되는지.
 * 약 × 시기 조합당 한 행. 약(PatientMedication)에 종속되며 약과 함께 저장·삭제된다.
 */
@Entity
@Table(name = "medication_timing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationTiming {

    @EmbeddedId
    private MedicationTimingId id;

    /** 복합키의 medication_id를 이 연관관계에서 채운다 */
    @MapsId("medicationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id")
    private PatientMedication medication;

    public MedicationTiming(PatientMedication medication, TimingSlot slot) {
        this.medication = medication;
        this.id = new MedicationTimingId(null, slot);
    }

    public TimingSlot getTimingSlot() {
        return id.getTimingSlot();
    }
}