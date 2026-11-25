package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.ratelimit.FixedWindowCounter;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.account.consent.ConsentGuard;
import com.gamcho.yakyeon.domain.device.dto.DeviceRegisterRequest;
import com.gamcho.yakyeon.domain.device.dto.DeviceResponse;
import com.gamcho.yakyeon.domain.device.dto.DeviceSettingsRequest;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import com.gamcho.yakyeon.domain.device.logic.DeviceInventoryRules;
import com.gamcho.yakyeon.domain.device.logic.DeviceSecrets;
import com.gamcho.yakyeon.domain.device.repository.DeviceInventoryRepository;
import com.gamcho.yakyeon.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 앱에서 약통을 등록·조회·해제한다. 복약자의 건강정보를 수집하는 기기를 연결하는 일이라
 * 등록과 해제는 수정 권한(본인 또는 읽기·쓰기 권한 보호자), 조회는 조회 권한이 필요하다.
 * 복약자 한 명에게는 약통 하나만 등록할 수 있다.
 *
 * 등록할 때는 약통에 적힌 시리얼번호와 비밀값을 출고 약통 목록(device_inventory)과 대조한다. 목록에 있고 비밀값이 맞을 때만
 * 등록되고, 목록에 없는 시리얼이든 비밀값이 틀렸든 같은 오류(DEVICE_CREDENTIALS_INVALID)를 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final String RESOURCE_TYPE = "device";
    private static final short DEFAULT_MAX_COMPARTMENTS = 3;

    private final PatientAccessGuard patientAccessGuard;
    private final ConsentGuard consentGuard;
    private final DeviceRepository deviceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DeviceInventoryRepository deviceInventoryRepository;

    /** 약통 등록 시도 횟수 (계정당, 서버 메모리). 비밀값을 계속 바꿔 가며 맞춰 보는 시도를 막는다 */
    private static final int REGISTER_ATTEMPTS_PER_HOUR = 20;
    private final FixedWindowCounter registerLimiter = new FixedWindowCounter(20_000);

    @Transactional
    public DeviceResponse register(Long userId, Long patientId, DeviceRegisterRequest request) {
        patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, RESOURCE_TYPE, null);
        // 약통 등록 전 스마트 약통 연결 동의(DEVICE)가 있어야 한다. 요청한 사람 본인의 동의를 본다
        consentGuard.requireDevice(userId);

        if (!registerLimiter.tryAcquire("device-register|" + userId, REGISTER_ATTEMPTS_PER_HOUR, 3600,
                Instant.now().getEpochSecond())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        // 출고 목록 대조가 먼저다: 이 약통을 쓸 수 있는 사람(비밀값을 아는 사람)에게만 "이미 다른 사람이 등록했다"를 알려 준다
        String inventoryHash = deviceInventoryRepository.findSecretHash(request.getSerialNo()).orElse(null);
        if (!DeviceInventoryRules.isRegistrable(request.getSecret(), inventoryHash)) {
            throw new BusinessException(ErrorCode.DEVICE_CREDENTIALS_INVALID);
        }

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
            if (cause != null && cause.contains("fk_device_inventory")) {
                // 대조한 뒤 저장하기 전에 출고 목록에서 빠진 드문 경우
                throw new BusinessException(ErrorCode.DEVICE_CREDENTIALS_INVALID);
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
     * 부저·LED 알림 설정을 바꾼다. 보낸 항목만 바뀌고 보내지 않은 항목은 그대로다. 중계 서버가 이 값을 읽어 약통에 전달한다.
     * 중계 서버가 같은 행의 다른 컬럼(접속 여부, 일정 반영 버전 등)도 쓰고 있어서, 엔티티를 통째로 저장하지 않고
     * 두 컬럼만 바꾸는 SQL 한 문장으로 처리한다(중계 서버의 갱신을 옛 값으로 덮어쓰지 않으려는 것이다).
     */
    @Transactional
    public DeviceResponse updateSettings(Long userId, Long patientId, DeviceSettingsRequest request) {
        patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, RESOURCE_TYPE, null);
        int updated = jdbcTemplate.update(
                "UPDATE device SET buzzer_enabled = COALESCE(CAST(? AS BOOLEAN), buzzer_enabled), "
                        + "led_enabled = COALESCE(CAST(? AS BOOLEAN), led_enabled) "
                        + "WHERE patient_id = ? AND status = 'REGISTERED'",
                request.getBuzzerEnabled(), request.getLedEnabled(), patientId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_FOUND);
        }
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
        release(device);
    }

    /**
     * 약통 연결 동의(DEVICE)를 철회하면 본인(복약자 프로필)에게 연결된 약통을 해제한다.
     * 사람이 약통 해제를 요청한 것이 아니라 동의 철회의 결과라서 접근 권한 확인은 하지 않는다. 연결된 약통이 없으면 아무 일도 없다.
     */
    @Transactional
    public void releaseOwnDevice(Long userId) {
        for (Long patientId : jdbcTemplate.queryForList("SELECT patient_id FROM patient WHERE user_id = ?", Long.class, userId)) {
            deviceRepository.findByPatientIdAndStatus(patientId, DeviceStatus.REGISTERED).ifPresent(this::release);
        }
    }

    /** 칸 배정을 모두 끝내고 약통 등록을 해제한다 */
    private void release(Device device) {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        jdbcTemplate.update("UPDATE compartment_mapping SET status = 'ENDED', valid_to = ?, "
                        + "valid_from = coalesce(valid_from, ?), loaded_count = coalesce(loaded_count, 0) "
                        + "WHERE device_id = ? AND status <> 'ENDED'",
                now, now, device.getDeviceId());
        device.unregister(now);
        deviceRepository.flush();
    }
}