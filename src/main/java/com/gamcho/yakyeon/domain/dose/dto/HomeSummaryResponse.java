package com.gamcho.yakyeon.domain.dose.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.dose.entity.DoseStatus;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 홈 화면의 오늘 복약 현황. total은 오늘 예정 전체, done은 복용을 마친 수(정상+지연)다 ("복약 현황 2/3").
 * normal/delayed/missed는 명세서(§6-1)의 집계 필드명을 그대로 쓴다.
 * slots는 아침·점심·저녁 아이콘 상태이고 항상 3개가 순서대로 들어 있다.
 */
public record HomeSummaryResponse(
        @JsonProperty("date") LocalDate date,
        @JsonProperty("total") int total,
        @JsonProperty("done") int done,
        @JsonProperty("normal") int normal,
        @JsonProperty("delayed") int delayed,
        @JsonProperty("missed") int missed,
        @JsonProperty("pending") int pending,
        @JsonProperty("slots") List<Slot> slots,
        @JsonProperty("next_dose") NextDose nextDose
) {
    /** state: NONE(예정 없음) / PENDING(아직) / DONE(복용 완료) / MISSED(누락 있음) */
    public record Slot(
            @JsonProperty("timing_slot") TimingSlot timingSlot,
            @JsonProperty("state") String state
    ) {
    }

    /** "다음 복약" 한 건. 오늘 남은 판정 전 예정 중 가장 이른 것이고, 없으면 next_dose 자체가 null이다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NextDose(
            @JsonProperty("scheduled_dose_id") Long scheduledDoseId,
            @JsonProperty("hospital_name") String hospitalName,
            @JsonProperty("timing_slot") TimingSlot timingSlot,
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
            @JsonProperty("status") DoseStatus status,
            @JsonProperty("medications") List<DoseResponse.Medication> medications
    ) {
    }
}