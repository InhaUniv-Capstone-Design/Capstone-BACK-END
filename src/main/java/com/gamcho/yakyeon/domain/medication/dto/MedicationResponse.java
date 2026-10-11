package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.drug.entity.Drug;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 약 한 건의 조회·수정 응답.
 * drug_name은 사용자가 확인한 약 이름(input_name)이고, 약물 DB와 매칭된 경우 drug_id·manufacturer가 채워진다.
 * status는 기준일에 대한 복용 상태(UPCOMING/ACTIVE/ENDED)로 저장된 값이 아니라 계산한 값이다.
 */
public record MedicationResponse(
        @JsonProperty("medication_id") Long medicationId,
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("hospital_name") String hospitalName,
        @JsonProperty("drug_id") Long drugId,
        @JsonProperty("drug_name") String drugName,
        @JsonProperty("manufacturer") String manufacturer,
        @JsonProperty("match_status") String matchStatus,
        @JsonProperty("dose_per_intake") BigDecimal dosePerIntake,
        @JsonProperty("is_prn") boolean prn,
        @JsonProperty("times_per_day") Short timesPerDay,
        @JsonProperty("timing_slots") List<String> timingSlots,
        @JsonProperty("meal_relation") String mealRelation,
        @JsonProperty("meal_offset_min") Short mealOffsetMin,
        @JsonProperty("total_days") Integer totalDays,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("status") MedicationStatus status
) {

    public static MedicationResponse from(PatientMedication m, LocalDate referenceDate) {
        Prescription rx = m.getPrescription();
        Drug drug = m.getDrug();
        // 복용 시기는 아침 → 점심 → 저녁 순(enum 선언 순서)으로 내려준다
        List<String> slots = m.getTimings().stream()
                .map(t -> t.getTimingSlot())
                .sorted()
                .map(Enum::name)
                .toList();
        return new MedicationResponse(
                m.getMedicationId(),
                rx == null ? null : rx.getPrescriptionId(),
                rx == null ? null : rx.getHospitalName(),
                drug == null ? null : drug.getDrugId(),
                m.getInputName(),
                drug == null ? null : drug.getManufacturer(),
                m.getMatchStatus().name(),
                m.getDosePerIntake(),
                m.isPrn(),
                m.getTimesPerDay(),
                slots,
                m.getMealRelation() == null ? null : m.getMealRelation().name(),
                m.getMealOffsetMin(),
                m.getTotalDays(),
                m.getStartDate(),
                m.getEndDate(),
                m.statusOn(referenceDate));
    }
}