package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import com.gamcho.yakyeon.domain.device.logic.CompartmentRules;
import com.gamcho.yakyeon.domain.device.repository.DeviceRepository;
import com.gamcho.yakyeon.domain.notification.service.IrStatePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 중계 서버가 약통 칸 상태(pouch_present)를 저장한 뒤 부르는 알림 처리. 호출자 확인(GatewayCallerChecker)은 컨트롤러에서 이미 끝났다.
 * 약통과 칸 번호를 확인하고, 그 칸의 복약자 본인 앱에 화면 갱신용 데이터 푸시(IR_TRIGGER)를 보낸다.
 *
 * 이 클래스는 일부러 @Transactional이 없다: 네트워크 호출(푸시)이 있어서 DB 행을 잡고 있지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class CompartmentStateNotifyService {

    private static final int MAX_SERIAL_LENGTH = 40;

    private final DeviceRepository deviceRepository;
    private final IrStatePushService irStatePushService;

    /** @return 푸시를 한 대 이상 보냈으면 true */
    public boolean notifyPouchState(String serialNo, Integer compartmentNo) {
        if (serialNo == null || serialNo.isBlank() || serialNo.length() > MAX_SERIAL_LENGTH) {
            throw new BusinessException(ErrorCode.DEVICE_AUTH_FAILED);
        }
        Device device = deviceRepository.findBySerialNoAndStatus(serialNo, DeviceStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_AUTH_FAILED));
        if (compartmentNo == null || !CompartmentRules.isValidCompartmentNo(compartmentNo, device.getMaxCompartments())) {
            throw new BusinessException(ErrorCode.INVALID_COMPARTMENT_NO);
        }
        return irStatePushService.push(device.getDeviceId(), device.getPatientId(), compartmentNo);
    }
}