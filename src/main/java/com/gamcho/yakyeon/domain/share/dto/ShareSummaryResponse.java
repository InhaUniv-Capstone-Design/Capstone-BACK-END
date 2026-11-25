package com.gamcho.yakyeon.domain.share.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * 복약 이력 공유 요약(의료진에게 보여 줄 화면). 이미지(PNG)는 앱이 이 데이터로 만들고, 서버는 요약만 준다.
 * 서버는 공유한 내용이나 이미지를 저장하지 않는다(누가 언제 만들었는지의 접근 기록만 남는다).
 * 약 이름은 사용자가 확인해서 등록한 이름이고, 날짜는 한국 날짜다.
 */
public record ShareSummaryResponse(
        @JsonProperty("patient") PatientInfo patient,
        @JsonProperty("period") PeriodInfo period,
        @JsonProperty("interactions") InteractionSection interactions,
        @JsonProperty("current_medications") List<MedicationItem> currentMedications,
        @JsonProperty("past_medications") List<MedicationItem> pastMedications,
        @JsonProperty("disclaimer") String disclaimer
) {

    /** 공유 대상 복약자. 의료진이 누구의 기록인지 알 수 있게 이름과 생년월일을 담는다 */
    public record PatientInfo(
            @JsonProperty("name") String name,
            @JsonProperty("birth_date") LocalDate birthDate
    ) {
    }

    /** 공유 기간 (양 끝 포함). days는 날짜 수다 */
    public record PeriodInfo(
            @JsonProperty("from") LocalDate from,
            @JsonProperty("to") LocalDate to,
            @JsonProperty("days") int days
    ) {
    }

    /**
     * @param uncheckedCount 아래 약 중 병용금기를 확인하지 못한 약의 수(약물 DB와 연결되지 않은 약, 또는 DUR 데이터가 아직 없을 때 전체).
     *                       0이 아니면 "등재 없음"을 안전하다는 뜻으로 보여 주면 안 된다.
     */
    public record InteractionSection(
            @JsonProperty("items") List<InteractionItem> items,
            @JsonProperty("unchecked_count") int uncheckedCount
    ) {
    }

    /** 병용금기 등재 한 건. 함께 복용한 기간은 두 약이 실제로 같이 복용된 날의 범위다(공유 기간 밖까지 이어질 수 있다) */
    public record InteractionItem(
            @JsonProperty("medication_a_name") String medicationAName,
            @JsonProperty("medication_b_name") String medicationBName,
            @JsonProperty("overlap_from") LocalDate overlapFrom,
            @JsonProperty("overlap_to") LocalDate overlapTo,
            @JsonProperty("reason_text") String reasonText,
            @JsonProperty("source_name") String sourceName,
            @JsonProperty("source_base_date") LocalDate sourceBaseDate
    ) {
    }

    /** end_date는 실제로 복용한 마지막 날이다(종료를 눌렀다면 종료를 누른 전날). hospital_name은 없으면 null */
    public record MedicationItem(
            @JsonProperty("medication_id") Long medicationId,
            @JsonProperty("drug_name") String drugName,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("hospital_name") String hospitalName
    ) {
    }
}