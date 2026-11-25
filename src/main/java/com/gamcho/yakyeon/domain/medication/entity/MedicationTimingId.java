package com.gamcho.yakyeon.domain.medication.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * medication_timing 복합키 (medication_id, timing_slot)
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationTimingId implements Serializable {

    @Column(name = "medication_id")
    private Long medicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "timing_slot", length = 10)
    private TimingSlot timingSlot;

    public MedicationTimingId(Long medicationId, TimingSlot timingSlot) {
        this.medicationId = medicationId;
        this.timingSlot = timingSlot;
    }
}