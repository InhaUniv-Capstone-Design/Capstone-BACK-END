package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.device.dto.PillboxResponse;
import com.gamcho.yakyeon.domain.device.entity.CompartmentMapping;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import com.gamcho.yakyeon.domain.device.entity.MappingStatus;
import com.gamcho.yakyeon.domain.device.logic.CompartmentRules;
import com.gamcho.yakyeon.domain.device.repository.CompartmentMappingRepository;
import com.gamcho.yakyeon.domain.device.repository.DeviceRepository;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.medication.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 약통 화면 조회 (칸별 약봉지, 남은 개수). 복약자의 약 정보가 보이므로 조회 권한이 필요하다
 * (본인 또는 연동된 보호자). 칸은 약통의 칸 수만큼 항상 1번부터 순서대로 내려가고, 비어 있는 칸은 EMPTY다.
 */
@Service
@RequiredArgsConstructor
public class PillboxQueryService {

    private static final String RESOURCE_TYPE = "pillbox";

    private final PatientAccessGuard patientAccessGuard;
    private final DeviceRepository deviceRepository;
    private final CompartmentMappingRepository compartmentMappingRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final JdbcTemplate jdbcTemplate;

    private record ReportedState(Boolean pouchPresent, Boolean alertActive) {
    }

    @Transactional(readOnly = true)
    public PillboxResponse get(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);
        Device device = deviceRepository.findByPatientIdAndStatus(patientId, DeviceStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

        Map<Integer, CompartmentMapping> byNo = compartmentMappingRepository
                .findByDeviceIdAndStatusNot(device.getDeviceId(), MappingStatus.ENDED).stream()
                .collect(Collectors.toMap(m -> (int) m.getCompartmentNo(), Function.identity()));

        List<Long> prescriptionIds = byNo.values().stream().map(CompartmentMapping::getPrescriptionId).toList();
        Map<Long, String> hospitalByPrescription = prescriptionRepository.findAllById(prescriptionIds).stream()
                .filter(p -> p.getHospitalName() != null)
                .collect(Collectors.toMap(Prescription::getPrescriptionId, Prescription::getHospitalName));
        Map<Long, List<PillboxResponse.Medication>> medsByPrescription = medicationsByPrescription(patientId, prescriptionIds);
        Map<Integer, ReportedState> reported = reportedStates(device.getDeviceId());

        List<PillboxResponse.Compartment> compartments = new ArrayList<>();
        for (int no = 1; no <= device.getMaxCompartments(); no++) {
            CompartmentMapping m = byNo.get(no);
            ReportedState rs = reported.get(no);
            Boolean pouch = rs == null ? null : rs.pouchPresent();
            Boolean alert = rs == null ? null : rs.alertActive();
            if (m == null) {
                compartments.add(new PillboxResponse.Compartment(no, PillboxResponse.State.EMPTY, null, null, null,
                        List.of(), null, null, false, pouch, alert));
            } else {
                compartments.add(new PillboxResponse.Compartment(no,
                        PillboxResponse.State.valueOf(m.getStatus().name()),
                        m.getMappingId(), m.getPrescriptionId(),
                        hospitalByPrescription.get(m.getPrescriptionId()),
                        medsByPrescription.getOrDefault(m.getPrescriptionId(), List.of()),
                        m.getLoadedCount(), m.getRemainingCount(),
                        CompartmentRules.isLowStock(m.getStatus().name(), m.getRemainingCount(), m.getLowStockThreshold()),
                        pouch, alert));
            }
        }
        return new PillboxResponse(
                new PillboxResponse.DeviceSummary(device.getDeviceId(), device.getSerialNo(),
                        device.isOnline(), device.getMaxCompartments()),
                compartments);
    }

    /** 칸에 들어 있는 약봉지의 약 목록: 삭제·종료되지 않았고 필요 시 복용(PRN)이 아닌 약 */
    private Map<Long, List<PillboxResponse.Medication>> medicationsByPrescription(Long patientId, List<Long> prescriptionIds) {
        Map<Long, List<PillboxResponse.Medication>> result = new HashMap<>();
        if (prescriptionIds.isEmpty()) {
            return result;
        }
        LocalDate today = ApiTime.today();
        for (PatientMedication m : patientMedicationRepository.findAliveByPatientId(patientId)) {
            if (m.getPrescription() == null || !prescriptionIds.contains(m.getPrescription().getPrescriptionId())
                    || m.isPrn() || m.statusOn(today) == MedicationStatus.ENDED) {
                continue;
            }
            result.computeIfAbsent(m.getPrescription().getPrescriptionId(), k -> new ArrayList<>())
                    .add(new PillboxResponse.Medication(m.getMedicationId(), m.getInputName()));
        }
        return result;
    }

    /** 약통이 보고한 칸별 상태 (약봉지가 들어 있는지, 알림 중인지). 보고가 없는 칸은 빠져 있다 */
    private Map<Integer, ReportedState> reportedStates(Long deviceId) {
        Map<Integer, ReportedState> result = new HashMap<>();
        jdbcTemplate.query("SELECT compartment_no, pouch_present, alert_active FROM device_compartment_state "
                        + "WHERE device_id = ?",
                rs -> {
                    result.put(rs.getInt("compartment_no"),
                            new ReportedState(rs.getObject("pouch_present", Boolean.class),
                                    rs.getObject("alert_active", Boolean.class)));
                }, deviceId);
        return result;
    }
}