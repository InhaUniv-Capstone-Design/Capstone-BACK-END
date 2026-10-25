package com.gamcho.yakyeon.domain.dose.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.dose.dto.DoseResponse;
import com.gamcho.yakyeon.domain.dose.dto.HomeSummaryResponse;
import com.gamcho.yakyeon.domain.dose.entity.DecisionSource;
import com.gamcho.yakyeon.domain.dose.entity.DoseStatus;
import com.gamcho.yakyeon.domain.dose.logic.DoseBrief;
import com.gamcho.yakyeon.domain.dose.logic.HomeSummaryCalculator;
import com.gamcho.yakyeon.domain.dose.repository.DoseQueryRepository;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 복용 일정·기록 조회 (오늘의 일정, 주간 일정, 복용 알림 내역, 홈 요약). 복약자의 건강정보라서
 * 모두 접근 가드를 거친다 (본인 또는 활성 연동 보호자의 조회 권한). 연동이 없는 사람에게는 복약자가
 * 존재하는지도 알려주지 않는다 (가드가 PATIENT_NOT_FOUND를 준다).
 * 날짜는 모두 한국 날짜 기준이고, 응답의 시각은 한국 시간(+09:00)으로 내려간다.
 */
@Service
@RequiredArgsConstructor
public class DoseQueryService {

    private static final String RESOURCE_TYPE = "dose";
    private static final int MAX_LIST_DAYS = 31;
    private static final int MAX_HISTORY_DAYS = 90;
    private static final int DEFAULT_HISTORY_DAYS = 14;
    private static final List<String> DECIDED_STATUSES = List.of("NORMAL", "DELAYED", "MISSED");

    private final PatientAccessGuard patientAccessGuard;
    private final DoseQueryRepository doseQueryRepository;

    /**
     * 일정 목록 (시각순). date 하나, 또는 from~to(양 끝 포함, 최대 31일). 아무것도 없으면 오늘.
     * 아직 오지 않은 예정(PENDING)도 포함한다 - 일정 화면의 "예정"이다.
     */
    @Transactional(readOnly = true)
    public List<DoseResponse> list(Long userId, Long patientId, LocalDate date, LocalDate from, LocalDate to) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);

        LocalDate start;
        LocalDate end;
        if (date != null) {
            if (from != null || to != null) {
                throw new BusinessException(ErrorCode.INVALID_QUERY, "date와 from/to는 함께 쓸 수 없습니다.");
            }
            start = date;
            end = date;
        } else if (from == null && to == null) {
            start = ApiTime.today();
            end = start;
        } else if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, "from과 to를 함께 입력해주세요.");
        } else {
            start = from;
            end = to;
        }
        validateRange(start, end, MAX_LIST_DAYS);
        return query(patientId, start, end, List.of(), false);
    }

    /**
     * 복용 기록 (최근 것부터). 판정이 끝난 예정(정상·지연·누락)만 나온다 - 아직 판정 전인 예정은 기록이 아니다.
     * 기간을 안 주면 최근 14일(오늘 포함), 최대 90일. status로 하나만 골라 볼 수 있다.
     */
    @Transactional(readOnly = true)
    public List<DoseResponse> history(Long userId, Long patientId, LocalDate from, LocalDate to, DoseStatus status) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);

        if (status == DoseStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, "status는 NORMAL, DELAYED, MISSED 중 하나여야 합니다.");
        }
        LocalDate start;
        LocalDate end;
        if (from == null && to == null) {
            end = ApiTime.today();
            start = end.minusDays(DEFAULT_HISTORY_DAYS - 1L);
        } else if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, "from과 to를 함께 입력해주세요.");
        } else {
            start = from;
            end = to;
        }
        validateRange(start, end, MAX_HISTORY_DAYS);
        List<String> statuses = status == null ? DECIDED_STATUSES : List.of(status.name());
        return query(patientId, start, end, statuses, true);
    }

    /** 홈 화면: 오늘의 복약 현황 (진행률, 아침·점심·저녁 아이콘, 다음 복약) */
    @Transactional(readOnly = true)
    public HomeSummaryResponse homeSummary(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);

        LocalDate today = ApiTime.today();
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        List<DoseQueryRepository.DoseRow> rows = findDay(patientId, today);

        HomeSummaryCalculator.Summary s = HomeSummaryCalculator.compute(
                rows.stream()
                        .map(r -> new DoseBrief(r.doseId(), r.slot(), r.status(), r.scheduledAt(), r.closeAt()))
                        .toList(),
                now);

        HomeSummaryResponse.NextDose next = null;
        if (s.nextDoseId() != null) {
            DoseQueryRepository.DoseRow r = rows.stream()
                    .filter(row -> row.doseId() == s.nextDoseId())
                    .findFirst().orElseThrow();
            next = new HomeSummaryResponse.NextDose(r.doseId(), r.hospitalName(), TimingSlot.valueOf(r.slot()),
                    kst(r.scheduledAt()), DoseStatus.valueOf(r.status()),
                    toMedications(doseQueryRepository.findItems(List.of(r.doseId())).get(r.doseId())));
        }
        return new HomeSummaryResponse(today, s.total(), s.done(), s.normal(), s.delayed(), s.missed(), s.pending(),
                s.slots().stream()
                        .map(st -> new HomeSummaryResponse.Slot(TimingSlot.valueOf(st.slot()), st.state()))
                        .toList(),
                next);
    }

    // ------------------------------------------------------------------ 내부

    private List<DoseQueryRepository.DoseRow> findDay(Long patientId, LocalDate day) {
        return doseQueryRepository.findDoses(patientId, startOf(day), startOf(day.plusDays(1)), List.of(), false);
    }

    private List<DoseResponse> query(Long patientId, LocalDate start, LocalDate end,
                                     List<String> statuses, boolean descending) {
        List<DoseQueryRepository.DoseRow> rows = doseQueryRepository.findDoses(
                patientId, startOf(start), startOf(end.plusDays(1)), statuses, descending);
        Map<Long, List<DoseQueryRepository.ItemRow>> items = doseQueryRepository.findItems(
                rows.stream().map(DoseQueryRepository.DoseRow::doseId).toList());
        return rows.stream().map(r -> toResponse(r, items.get(r.doseId()))).toList();
    }

    private static DoseResponse toResponse(DoseQueryRepository.DoseRow r, List<DoseQueryRepository.ItemRow> items) {
        return new DoseResponse(r.doseId(), r.prescriptionId(), r.hospitalName(),
                TimingSlot.valueOf(r.slot()), kst(r.scheduledAt()), kst(r.normalUntil()), kst(r.closeAt()),
                DoseStatus.valueOf(r.status()), kst(r.takenAt()),
                r.decisionSource() == null ? null : DecisionSource.valueOf(r.decisionSource()),
                r.corrected(), toMedications(items));
    }

    private static List<DoseResponse.Medication> toMedications(List<DoseQueryRepository.ItemRow> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(i -> new DoseResponse.Medication(i.medicationId(), i.name(), i.dosePerIntake()))
                .toList();
    }

    private static void validateRange(LocalDate start, LocalDate end, int maxDays) {
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, "종료일이 시작일보다 빠릅니다.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxDays) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, "조회 기간은 최대 " + maxDays + "일입니다.");
        }
    }

    /** 한국 날짜의 0시 */
    private static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(ApiTime.SERVICE_ZONE).toOffsetDateTime();
    }

    /** DB 드라이버는 시각을 UTC로 주기 때문에, 응답에는 한국 시간(+09:00)으로 바꿔서 내려준다 */
    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime();
    }
}