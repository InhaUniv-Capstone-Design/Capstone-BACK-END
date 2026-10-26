package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.device.dto.DeviceRegisterRequest;
import com.gamcho.yakyeon.domain.device.dto.DeviceResponse;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import com.gamcho.yakyeon.domain.device.logic.DeviceSecrets;
import com.gamcho.yakyeon.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 앱에서 약통을 등록·조회·해제한다. 복약자의 건강정보를 수집하는 기기를 연결하는 일이라
 * 등록과 해제는 수정 권한(본인 또는 읽기·쓰기 권한 보호자), 조회는 조회 권한이 필요하다.
 * 복약자 한 명에게는 약통 하나만 등록할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final String RESOURCE_TYPE = "device";
    private static final short DEFAULT_MAX_COMPARTMENTS = 3;

    private final PatientAccessGuard patientAccessGuard;
    private final DeviceRepository deviceRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public DeviceResponse register(Long userId, Long patientId, DeviceRegisterRequest request) {
        patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, RESOURCE_TYPE, null);
        // 센서 이벤트 수집 동의 확인 자리: 동의 항목이 확정되면 여기서 동의하지 않은 복약자의 등록을 거절한다.

        if (deviceRepository.existsByPatientIdAndStatus(patientId, DeviceStatus.REGISTERED)) {
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_REGISTERED);
        }
        if (deviceRepository.existsBySerialNoAndStatus(request.getSerialNo(), DeviceStatus.REGISTERED)) {
            throw new BusinessException(ErrorCode.DEVICE_SERIAL_IN_USE);
        }

        short maxCompartments = request.getMaxCompartments() == null
                ? DEFAULT_MAX_COMPARTMENTS : request.getMaxCompartments().shortValue();
        Device device = Device.builder()
                .serialNo(request.getSerialNo())
                .secretHash(DeviceSecrets.hash(request.getSecret()))
                .patientId(patientId)
                .ownerConfirmedBy(userId)
                .maxCompartments(maxCompartments)
                .registeredAt(OffsetDateTime.now(ApiTime.SERVICE_ZONE))
                .build();
        try {
            return DeviceResponse.from(deviceRepository.saveAndFlush(device));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 요청이 먼저 들어온 경우 - DB의 유니크 인덱스가 최종으로 막는다
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("uq_device_serial_registered")) {
                throw new BusinessException(ErrorCode.DEVICE_SERIAL_IN_USE);
            }
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_REGISTERED);
        }
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.VIEW, RESOURCE_TYPE, null);
        return DeviceResponse.from(deviceRepository.findByPatientIdAndStatus(patientId, DeviceStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND)));
    }

    /**
     * 등록 해제. 약통에 배정된 칸도 모두 함께 끝낸다 (기록은 남는다). 해제된 기기는 이후 신호를 보낼 수 없다.
     * 칸 배정 종료는 DB의 탈퇴 처리와 같은 방식이다 - 종료 상태에는 시작 시각과 충전량이 있어야 하는 DB 규칙 때문에
     * 아직 충전하지 않은 배정에는 지금 시각과 0을 채워 넣는다.
     */
    @Transactional
    public void unregister(Long userId, Long patientId) {
        patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, RESOURCE_TYPE, null);
        Device device = deviceRepository.findByPatientIdAndStatus(patientId, DeviceStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        jdbcTemplate.update("UPDATE compartment_mapping SET status = 'ENDED', valid_to = ?, "
                        + "valid_from = coalesce(valid_from, ?), loaded_count = coalesce(loaded_count, 0) "
                        + "WHERE device_id = ? AND status <> 'ENDED'",
                now, now, device.getDeviceId());
        device.unregister(now);
        deviceRepository.flush();
    }
}