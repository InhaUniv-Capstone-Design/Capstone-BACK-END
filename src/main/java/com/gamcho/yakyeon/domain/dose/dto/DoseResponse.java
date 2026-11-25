package com.gamcho.yakyeon.domain.dose.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.dose.entity.DecisionSource;
import com.gamcho.yakyeon.domain.dose.entity.DoseStatus;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 복용 예정 한 건 = 약봉지 하나의 한 시간대 한 번 복용. 시각은 모두 한국 시간(+09:00)으로 내려간다.
 *
 * @param takenAt        실제로 복용한 시각. 정상·지연일 때만 값이 있다
 * @param decisionSource 누가 판정했는지(약통 DEVICE, 앱 MANUAL, 시스템 SYSTEM). 판정 전이면 null
 * @param corrected      사용자가 앱에서 정정한 적이 있는지 ("정정됨" 표시용)
 * @param medications    이 시간대에 먹는 약. 예정 시각에 포함돼 있던 약만 나온다
 */
public record DoseResponse(
        @JsonProperty("scheduled_dose_id") Long scheduledDoseId,
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("hospital_name") String hospitalName,
        @JsonProperty("timing_slot") TimingSlot timingSlot,
        @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
        @JsonProperty("normal_until") OffsetDateTime normalUntil,
        @JsonProperty("close_at") OffsetDateTime closeAt,
        @JsonProperty("status") DoseStatus status,
        @JsonProperty("taken_at") OffsetDateTime takenAt,
        @JsonProperty("decision_source") DecisionSource decisionSource,
        @JsonProperty("corrected") boolean corrected,
        @JsonProperty("medications") List<Medication> medications
) {
    public record Medication(
            @JsonProperty("medication_id") Long medicationId,
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("dose_per_intake") BigDecimal dosePerIntake
    ) {
    }
}