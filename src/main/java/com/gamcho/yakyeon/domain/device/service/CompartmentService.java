package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.device.dto.CompartmentMappingResponse;
import com.gamcho.yakyeon.domain.device.entity.CompartmentMapping;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import com.gamcho.yakyeon.domain.device.entity.MappingStatus;
import com.gamcho.yakyeon.domain.device.logic.CompartmentRules;
import com.gamcho.yakyeon.domain.device.logic.PouchCounter;
import com.gamcho.yakyeon.domain.device.repository.CompartmentMappingRepository;
import com.gamcho.yakyeon.domain.device.repository.DeviceRepository;
import com.gamcho.yakyeon.domain.dose.logic.MedWindow;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 약봉지(처방)를 약통의 칸에 배정하고, 충전을 확인하고, 해제한다.
 * 복약자의 약 정보를 약통에 연결하는 일이라 모두 수정 권한(본인 또는 읽기·쓰기 권한 보호자)이 필요하다.
 * 연동이 없는 사람에게는 "없는 번호"와 같은 오류를 줘서 번호가 존재한다는 사실이 새지 않게 한다.
 *
 * 흐름: 칸 배정(바로 ACTIVE, 채운 수 기록) → 필요하면 추가 충전 → 해제(ENDED).
 * 앱 화면에 충전 확인 단계가 없어서 배정할 때 충전까지 한 번에 확인한 것으로 처리한다. 충전 확인(load)은 예전에 배정되어 PENDING_LOAD로
 * 남은 칸을 위해서만 남겨 둔다.
 * 처방이 끝나면 DB 함수가 배정을 자동으로 끝낸다 (복용 종료 API가 호출한다).
 */
@Service
@RequiredArgsConstructor
public class CompartmentService {

    private static final String RESOURCE_TYPE = "compartment";

    private final PatientAccessGuard patientAccessGuard;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final DeviceRepository deviceRepository;
    private final CompartmentMappingRepository compartmentMappingRepository;

    /**
     * 약봉지를 칸에 배정하고, 바로 충전을 확인한 상태(ACTIVE)로 만든다. 이 시각부터 이 칸의 신호를 이 약봉지로 본다.
     *
     * 넣은 약봉지 수는 loadedCount를 받으면 그 값, 없으면 처방의 남은 복용 일정으로 센다(복용 시간대 수 × 남은 일수, 약마다 기간이
     * 다르면 그 차이 반영). 약봉지 하나는 복용 한 번에 해당한다.
     */
    @Transactional
    public CompartmentMappingResponse assign(Long userId, Long prescriptionId, int compartmentNo, Integer loadedCount) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        Long patientId = prescription.getPatient().getPatientId();
        authorize(userId, patientId, prescriptionId, ErrorCode.PRESCRIPTION_NOT_FOUND);

        if (prescription.isEnded() || !hasOpenMedication(patientId, prescriptionId)) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_ENDED);
        }
        Device device = deviceRepository.findByPatientIdAndStatus(patientId, DeviceStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!CompartmentRules.isValidCompartmentNo(compartmentNo, device.getMaxCompartments())) {
            throw new BusinessException(ErrorCode.INVALID_COMPARTMENT_NO);
        }
        if (compartmentMappingRepository.existsByPrescriptionIdAndStatusNot(prescriptionId, MappingStatus.ENDED)) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_ASSIGNED);
        }
        if (compartmentMappingRepository.existsByDeviceIdAndCompartmentNoAndStatusNot(
                device.getDeviceId(), (short) compartmentNo, MappingStatus.ENDED)) {
            throw new BusinessException(ErrorCode.COMPARTMENT_OCCUPIED);
        }

        CompartmentMapping mapping = CompartmentMapping.builder()
                .deviceId(device.getDeviceId())
                .patientId(patientId)
                .compartmentNo((short) compartmentNo)
                .prescriptionId(prescriptionId)
                .confirmedByUserId(userId)
                .build();
        int pouches = loadedCount != null ? loadedCount : plannedPouchCount(patientId, prescriptionId);
        mapping.load(pouches, userId, OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        try {
            return CompartmentMappingResponse.from(compartmentMappingRepository.saveAndFlush(mapping));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 요청이 먼저 들어온 경우 - DB의 유니크 인덱스가 최종으로 막는다
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("uq_mapping_prescription_live")) {
                throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_ASSIGNED);
            }
            throw new BusinessException(ErrorCode.COMPARTMENT_OCCUPIED);
        }
    }

    /** 처음 충전을 확인한다. 채운 약봉지 수가 남은 수의 시작값이 되고, 이 시각부터 이 칸의 신호를 이 약봉지로 본다 */
    @Transactional
    public CompartmentMappingResponse load(Long userId, Long mappingId, int loadedCount) {
        CompartmentMapping mapping = authorizeAndLock(userId, mappingId);
        if (mapping.getStatus() == MappingStatus.ENDED) {
            throw new BusinessException(ErrorCode.MAPPING_ALREADY_ENDED);
        }
        if (mapping.getStatus() != MappingStatus.PENDING_LOAD) {
            throw new BusinessException(ErrorCode.MAPPING_NOT_PENDING_LOAD);
        }
        mapping.load(loadedCount, userId, OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        compartmentMappingRepository.flush();
        return CompartmentMappingResponse.from(mapping);
    }

    /** 사용 중인 칸에 약봉지를 더 채운다 (채운 수와 남은 수가 함께 늘어난다) */
    @Transactional
    public CompartmentMappingResponse refill(Long userId, Long mappingId, int addedCount) {
        CompartmentMapping mapping = authorizeAndLock(userId, mappingId);
        if (mapping.getStatus() == MappingStatus.ENDED) {
            throw new BusinessException(ErrorCode.MAPPING_ALREADY_ENDED);
        }
        if (mapping.getStatus() != MappingStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MAPPING_NOT_ACTIVE);
        }
        mapping.refill(addedCount, userId);
        compartmentMappingRepository.flush();
        return CompartmentMappingResponse.from(mapping);
    }

    /** 칸 배정을 해제한다 (기록은 남는다). 해제하면 그 칸에 다른 약봉지를 배정할 수 있다 */
    @Transactional
    public void release(Long userId, Long mappingId) {
        CompartmentMapping mapping = authorizeAndLock(userId, mappingId);
        if (mapping.getStatus() == MappingStatus.ENDED) {
            throw new BusinessException(ErrorCode.MAPPING_ALREADY_ENDED);
        }
        mapping.end(OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        compartmentMappingRepository.flush();
    }

    // ------------------------------------------------------------------ 내부

    /** 권한을 먼저 확인하고(행을 잠그기 전에) 그다음 배정을 잠근다. 권한 없는 사람이 남의 배정을 잠글 수 없다 */
    private CompartmentMapping authorizeAndLock(Long userId, Long mappingId) {
        Long patientId = compartmentMappingRepository.findPatientId(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPARTMENT_MAPPING_NOT_FOUND));
        authorize(userId, patientId, mappingId, ErrorCode.COMPARTMENT_MAPPING_NOT_FOUND);
        return compartmentMappingRepository.findByIdForUpdate(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPARTMENT_MAPPING_NOT_FOUND));
    }

    private void authorize(Long userId, Long patientId, Long resourceId, ErrorCode notFound) {
        try {
            patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, RESOURCE_TYPE, resourceId);
        } catch (BusinessException e) {
            // 연동이 없는 사람에게는 "없는 번호"와 같은 오류를 줘서 번호가 존재한다는 사실이 새지 않게 한다
            if (e.getErrorCode() == ErrorCode.PATIENT_NOT_FOUND) {
                throw new BusinessException(notFound);
            }
            throw e;
        }
    }

    /** 이 처방에 앞으로 필요한 약봉지 수 (삭제된 약과 필요 시 복용 약은 예정이 없으니 뺀다) */
    private int plannedPouchCount(Long patientId, Long prescriptionId) {
        List<MedWindow> meds = patientMedicationRepository.findAliveByPatientId(patientId).stream()
                .filter(m -> m.getPrescription() != null && prescriptionId.equals(m.getPrescription().getPrescriptionId()))
                .filter(m -> !m.isPrn())
                .map(m -> new MedWindow(
                        m.getTimings().stream().map(t -> t.getTimingSlot().name()).collect(Collectors.toSet()),
                        m.getStartDate(), m.getEndDate(), m.getEndedAt()))
                .toList();
        return PouchCounter.remaining(meds, ApiTime.today(), ApiTime.SERVICE_ZONE);
    }

    /** 이 처방에 아직 끝나지 않은 약이 하나라도 있는지 */
    private boolean hasOpenMedication(Long patientId, Long prescriptionId) {
        LocalDate today = ApiTime.today();
        return patientMedicationRepository.findAliveByPatientId(patientId).stream()
                .anyMatch(m -> m.getPrescription() != null
                        && prescriptionId.equals(m.getPrescription().getPrescriptionId())
                        && m.statusOn(today) != MedicationStatus.ENDED);
    }
}