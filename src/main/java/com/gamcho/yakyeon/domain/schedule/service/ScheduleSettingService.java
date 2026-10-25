package com.gamcho.yakyeon.domain.schedule.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.dose.service.DoseScheduleService;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.MedicationTiming;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import com.gamcho.yakyeon.domain.schedule.dto.ScheduleSettingRequest;
import com.gamcho.yakyeon.domain.schedule.dto.ScheduleSettingResponse;
import com.gamcho.yakyeon.domain.schedule.entity.ScheduleSetting;
import com.gamcho.yakyeon.domain.schedule.logic.ScheduleRules;
import com.gamcho.yakyeon.domain.schedule.repository.ScheduleSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 처방(약봉지)별 복용 시각 설정. 복약자의 건강 관련 설정이라 접근 가드를 거친다 -
 * 조회는 조회 권한, 변경은 수정 권한(본인 또는 읽기·쓰기 권한 보호자)이 필요하다.
 *
 * 시각을 바꾸면 기존 설정을 닫고 새 버전을 추가한다. 이미 판정된 예정은 예전 버전을 계속 가리키므로
 * "지난 기록은 그대로, 앞으로의 예정부터 새 시각"이 된다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleSettingService {

    private static final String RESOURCE_TYPE = "schedule_setting";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final PatientAccessGuard patientAccessGuard;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final ScheduleSettingRepository scheduleSettingRepository;
    private final DoseScheduleService doseScheduleService;

    @Transactional(readOnly = true)
    public ScheduleSettingResponse get(Long userId, Long prescriptionId) {
        findAndAuthorize(userId, prescriptionId, AccessAction.VIEW);
        return toResponse(prescriptionId, scheduleSettingRepository.findByPrescriptionIdAndEffectiveToIsNull(prescriptionId));
    }

    @Transactional
    public ScheduleSettingResponse put(Long userId, Long prescriptionId, ScheduleSettingRequest request) {
        Prescription prescription = findAndAuthorize(userId, prescriptionId, AccessAction.UPDATE);
        if (prescription.isEnded()) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_ENDED);
        }
        Long patientId = prescription.getPatient().getPatientId();

        // 같은 시간대를 두 번 보낸 요청은 어느 쪽이 맞는지 알 수 없어서 거절한다
        Map<TimingSlot, LocalTime> requested = new EnumMap<>(TimingSlot.class);
        for (ScheduleSettingRequest.Item item : request.getSettings()) {
            if (requested.put(item.getTimingSlot(), LocalTime.parse(item.getDoseTime())) != null) {
                throw new BusinessException(ErrorCode.INVALID_SCHEDULE,
                        ScheduleRules.label(item.getTimingSlot().name()) + " 시각이 두 번 들어 있습니다.");
            }
        }

        Set<String> requiredSlots = requiredSlots(patientId, prescriptionId);
        Map<String, LocalTime> byName = new HashMap<>();
        requested.forEach((slot, time) -> byName.put(slot.name(), time));
        ScheduleRules.firstViolation(byName, requiredSlots).ifPresent(message -> {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE, message);
        });

        try {
            applyVersions(patientId, prescriptionId, requested);
        } catch (DataIntegrityViolationException e) {
            // 같은 처방의 시각을 동시에 바꾸면 "현재 설정은 하나뿐"이라는 DB 규칙에 걸린다
            throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT);
        }
        // 시각이 바뀌었으니 앞으로 7일치 예정을 새 시각에 맞춘다 (이미 판정된 예정은 건드리지 않는다)
        doseScheduleService.reconcile(prescriptionId);
        return toResponse(prescriptionId, scheduleSettingRepository.findByPrescriptionIdAndEffectiveToIsNull(prescriptionId));
    }

    /**
     * 바뀐 시간대는 기존 설정을 닫고 새 설정을 넣는다. 같은 시각이면 아무것도 하지 않는다 (불필요한 새 버전 방지).
     * 요청에 없는 시간대의 기존 설정은 닫는다 (그 시간대에 먹는 약이 있었다면 검증에서 이미 거절됐다).
     */
    private void applyVersions(Long patientId, Long prescriptionId, Map<TimingSlot, LocalTime> requested) {
        List<ScheduleSetting> current = scheduleSettingRepository.findByPrescriptionIdAndEffectiveToIsNull(prescriptionId);

        List<ScheduleSetting> toClose = new ArrayList<>();
        Map<TimingSlot, LocalTime> toInsert = new EnumMap<>(TimingSlot.class);
        Map<TimingSlot, ScheduleSetting> currentBySlot = new EnumMap<>(TimingSlot.class);
        for (ScheduleSetting c : current) {
            currentBySlot.put(c.getTimingSlot(), c);
        }
        for (ScheduleSetting c : current) {
            LocalTime want = requested.get(c.getTimingSlot());
            if (want == null || !want.equals(c.getDoseTime())) {
                toClose.add(c);
            }
        }
        for (Map.Entry<TimingSlot, LocalTime> e : requested.entrySet()) {
            ScheduleSetting existing = currentBySlot.get(e.getKey());
            if (existing == null || !e.getValue().equals(existing.getDoseTime())) {
                toInsert.put(e.getKey(), e.getValue());
            }
        }

        // 닫는 시각과 새 설정의 시작 시각을 같게 하되, DB 규칙(끝 > 시작)을 어기지 않도록 옛 설정의 시작보다 뒤로 보장한다
        OffsetDateTime at = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        for (ScheduleSetting c : toClose) {
            if (!at.isAfter(c.getEffectiveFrom())) {
                at = c.getEffectiveFrom().plusNanos(1_000);
            }
        }
        for (ScheduleSetting c : toClose) {
            c.close(at);
        }
        // 현재 설정은 처방·시간대마다 하나뿐이라는 DB 유니크 인덱스 때문에, 옛 행을 먼저 닫아서 DB에 반영한 뒤
        // 새 행을 넣어야 한다 (JPA는 저장 순서를 바꿔서 INSERT를 먼저 보낼 수 있다)
        scheduleSettingRepository.flush();

        for (Map.Entry<TimingSlot, LocalTime> e : toInsert.entrySet()) {
            scheduleSettingRepository.save(ScheduleSetting.builder()
                    .patientId(patientId)
                    .prescriptionId(prescriptionId)
                    .timingSlot(e.getKey())
                    .doseTime(e.getValue())
                    .normalWindowMin((short) ScheduleRules.NORMAL_WINDOW_MIN)
                    .closeAfterMin((short) ScheduleRules.CLOSE_AFTER_MIN)
                    .effectiveFrom(at)
                    .build());
        }
    }

    /** 이 처방의 약이 실제로 쓰는 시간대: 삭제·종료되지 않은 일반 약(필요 시 복용 제외)의 복용 시기 */
    private Set<String> requiredSlots(Long patientId, Long prescriptionId) {
        LocalDate today = ApiTime.today();
        Set<String> slots = new TreeSet<>();
        for (PatientMedication m : patientMedicationRepository.findAliveByPatientId(patientId)) {
            if (m.getPrescription() == null || !prescriptionId.equals(m.getPrescription().getPrescriptionId())) {
                continue;
            }
            if (m.isPrn() || m.statusOn(today) == MedicationStatus.ENDED) {
                continue;
            }
            for (MedicationTiming t : m.getTimings()) {
                slots.add(t.getTimingSlot().name());
            }
        }
        return slots;
    }

    private Prescription findAndAuthorize(Long userId, Long prescriptionId, AccessAction action) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        try {
            patientAccessGuard.check(userId, prescription.getPatient().getPatientId(), action,
                    RESOURCE_TYPE, prescriptionId);
        } catch (BusinessException e) {
            // 연동이 없는 사람에게는 "없는 번호"와 같은 오류를 줘서 번호가 존재한다는 사실이 새지 않게 한다
            if (e.getErrorCode() == ErrorCode.PATIENT_NOT_FOUND) {
                throw new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND);
            }
            throw e;
        }
        return prescription;
    }

    private static ScheduleSettingResponse toResponse(Long prescriptionId, List<ScheduleSetting> settings) {
        List<ScheduleSettingResponse.Item> items = settings.stream()
                .sorted(Comparator.comparing(ScheduleSetting::getTimingSlot))
                .map(s -> new ScheduleSettingResponse.Item(
                        s.getTimingSlot(), s.getDoseTime().format(HHMM),
                        s.getNormalWindowMin(), s.getCloseAfterMin()))
                .toList();
        return new ScheduleSettingResponse(prescriptionId, items);
    }
}