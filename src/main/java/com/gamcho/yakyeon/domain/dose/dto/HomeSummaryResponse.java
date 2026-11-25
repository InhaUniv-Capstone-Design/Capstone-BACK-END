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
 * slots는 아침·점심·저녁·취침 아이콘 상태이고 항상 4개가 순서대로 들어 있다(예정이 없는 시간대는 NONE).
 *
 * 새 홈 화면용 필드(앱은 이것만으로 화면을 만들 수 있다):
 *  - server_time: 응답을 만든 시각(한국 시간). "3시간 22분 남음" 같은 남은 시간은 scheduled_at - server_time으로 계산한다
 *    (기기 시계가 틀려도 서버 기준으로 맞게).
 *  - unchecked_doses: 아직 복용 체크하지 않은 약. 예정 시각이 지났고 아직 마감 전인 판정 전 예정이다(복용 체크 POST /doses/{id}/taken으로
 *    기록할 수 있다). 마감이 지나 시스템이 누락 처리하면 목록에서 빠진다. 이른 순.
 *  - upcoming_doses: 다음 복용 예정 약. 예정 시각이 아직 오지 않은 것, 이른 순.
 *  - all_done: 오늘 복용이 모두 끝났는가(오늘 예정이 있고, 위 두 목록이 모두 비었을 때). 오늘 예정이 아예 없으면 false.
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
        @JsonProperty("next_dose") NextDose nextDose,
        @JsonProperty("server_time") OffsetDateTime serverTime,
        @JsonProperty("unchecked_doses") List<DoseResponse> uncheckedDoses,
        @JsonProperty("upcoming_doses") List<DoseResponse> upcomingDoses,
        @JsonProperty("all_done") boolean allDone
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