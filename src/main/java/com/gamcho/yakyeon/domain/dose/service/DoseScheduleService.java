package com.gamcho.yakyeon.domain.dose.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.dose.entity.DoseStatus;
import com.gamcho.yakyeon.domain.dose.entity.ScheduledDose;
import com.gamcho.yakyeon.domain.dose.logic.DosePlanner;
import com.gamcho.yakyeon.domain.dose.logic.DoseReconciler;
import com.gamcho.yakyeon.domain.dose.logic.ExistingDose;
import com.gamcho.yakyeon.domain.dose.logic.MedWindow;
import com.gamcho.yakyeon.domain.dose.logic.PlannedDose;
import com.gamcho.yakyeon.domain.dose.logic.ReconcileResult;
import com.gamcho.yakyeon.domain.dose.logic.SettingRef;
import com.gamcho.yakyeon.domain.dose.repository.ScheduledDoseRepository;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import com.gamcho.yakyeon.domain.schedule.repository.ScheduleSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 복용 일정(예정) 생성·정리. 처방 하나의 "앞으로 7일치 예정"을 현재 설정과 약 상태에 맞춰 맞춘다.
 *
 * reconcile(처방)은 몇 번을 불러도 결과가 같다(멱등). 그래서 아래 어디서든 부르면 된다:
 *  - 복용 시각을 저장·변경했을 때, 약을 수정·삭제·종료했을 때, 처방을 종료했을 때 (바뀐 즉시 반영)
 *  - 매일 자정과 서버가 켜질 때 (새로 생긴 하루를 채우고, 서버가 꺼져 있던 날을 메운다)
 *
 * 판정된 예정은 복용 기록이라 절대 지우거나 바꾸지 않는다. 자세한 규칙은 DoseReconciler를 본다.
 */
@Service
@RequiredArgsConstructor
public class DoseScheduleService {

    /** 오늘부터 며칠치 예정을 유지할지. 주간 일정 화면(7일)을 채우는 데 맞춘 값 */
    public static final int WINDOW_DAYS = 7;

    /**
     * 같은 처방·같은 예정 시각이 이미 있으면 건너뛴다 (DB 유니크 인덱스).
     * 예외로 실패시키지 않고 건너뛰어야, 자정 작업과 사용자의 변경이 동시에 같은 예정을 만들어도
     * 사용자의 요청이 실패하지 않는다.
     */
    private static final String INSERT_SQL =
            "INSERT INTO scheduled_dose "
                    + "(patient_id, prescription_id, schedule_setting_id, timing_slot, scheduled_at, normal_until, close_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (prescription_id, scheduled_at) DO NOTHING";

    private final PrescriptionRepository prescriptionRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final ScheduleSettingRepository scheduleSettingRepository;
    private final ScheduledDoseRepository scheduledDoseRepository;
    private final JdbcTemplate jdbcTemplate;

    public record Summary(int deleted, int created) {
    }

    @Transactional
    public Summary reconcile(Long prescriptionId) {
        Optional<Prescription> found = prescriptionRepository.findById(prescriptionId);
        if (found.isEmpty()) {
            return new Summary(0, 0);
        }
        Prescription prescription = found.get();
        Long patientId = prescription.getPatient().getPatientId();

        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        LocalDate today = now.toLocalDate();

        List<SettingRef> settings = scheduleSettingRepository
                .findByPrescriptionIdAndEffectiveToIsNull(prescriptionId).stream()
                .map(s -> new SettingRef(s.getScheduleSettingId(), s.getTimingSlot().name(), s.getDoseTime(),
                        s.getNormalWindowMin(), s.getCloseAfterMin()))
                .toList();

        // 삭제된 약은 조회에서 이미 빠져 있고, 필요 시 복용(PRN) 약은 예정을 만들지 않는다
        List<MedWindow> meds = patientMedicationRepository.findAliveByPatientId(patientId).stream()
                .filter(m -> m.getPrescription() != null
                        && prescriptionId.equals(m.getPrescription().getPrescriptionId()))
                .filter(m -> !m.isPrn())
                .map(m -> new MedWindow(
                        m.getTimings().stream().map(t -> t.getTimingSlot().name()).collect(Collectors.toSet()),
                        m.getStartDate(), m.getEndDate(), m.getEndedAt()))
                .toList();

        List<PlannedDose> planned = DosePlanner.plan(settings, meds, prescription.getEndedAt(),
                today, WINDOW_DAYS, ApiTime.SERVICE_ZONE);

        // 오늘 0시 이후의 예정만 본다 (지난 날의 기록은 건드릴 일이 없다)
        OffsetDateTime startOfToday = today.atStartOfDay(ApiTime.SERVICE_ZONE).toOffsetDateTime();
        List<ExistingDose> existing = scheduledDoseRepository
                .findByPrescriptionIdAndScheduledAtGreaterThanEqual(prescriptionId, startOfToday).stream()
                .map(d -> new ExistingDose(d.getScheduledDoseId(), d.getTimingSlot().name(), d.getScheduledAt(),
                        d.getScheduleSettingId(), d.getStatus() == DoseStatus.PENDING))
                .toList();

        ReconcileResult result = DoseReconciler.reconcile(planned, existing, now, ApiTime.SERVICE_ZONE);

        int deleted = 0;
        if (!result.deleteIds().isEmpty()) {
            deleted = scheduledDoseRepository.deletePendingByIds(result.deleteIds());
        }
        int created = 0;
        if (!result.inserts().isEmpty()) {
            List<Object[]> args = result.inserts().stream()
                    .map(p -> new Object[]{patientId, prescriptionId, p.settingId(), p.slot(),
                            p.scheduledAt(), p.normalUntil(), p.closeAt()})
                    .toList();
            for (int n : jdbcTemplate.batchUpdate(INSERT_SQL, args)) {
                if (n > 0) {
                    created += n;
                }
            }
        }
        return new Summary(deleted, created);
    }

    /** 점검 대상: 현재 유효한 복용 시각이 있는 처방 (시각이 없으면 만들 예정도 없다) */
    @Transactional(readOnly = true)
    public List<Long> targetPrescriptionIds() {
        return scheduledDoseRepository.currentSettingPrescriptionIds();
    }
}