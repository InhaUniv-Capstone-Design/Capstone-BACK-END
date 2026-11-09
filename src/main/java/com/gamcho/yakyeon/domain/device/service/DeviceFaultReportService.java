package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.device.logic.DeviceFaultRules;
import com.gamcho.yakyeon.domain.device.repository.DeviceStatusReportRepository;
import com.gamcho.yakyeon.domain.notification.service.NotificationCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 중계 서버(MQTT)가 device_status_report에 저장해 둔 보고 한 건의 고장 여부를 판정하고, 새로 고장이 났으면 알림을 만든다.
 *
 * 판정은 약통이 HTTPS로 직접 보낸 상태 보고(DeviceStatusService)와 같은 규칙(DeviceFaultRules)을 쓴다. 다른 점은 비교 기준뿐이다:
 * 중계 서버가 device 행을 이미 새 값으로 덮어쓰기 때문에, "직전 값"은 device 행이 아니라 이 보고보다 앞선 이력에서 가져온다.
 *
 * 보고 한 건의 판정, 알림 만들기, "처리 완료" 표시는 한 트랜잭션이다. 알림을 만들다 실패하면 표시도 취소되어
 * 다음 주기에 다시 시도하므로 알림이 빠지지 않고, 표시가 되어 있으면 알림이 이미 만들어진 것이라 두 번 나가지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DeviceFaultReportService {

    private final DeviceStatusReportRepository deviceStatusReportRepository;
    private final NotificationCreator notificationCreator;

    /**
     * @return 이 보고로 새 고장이 확인되어 알림을 만들었으면 true. 이미 처리됐거나 고장이 아니면 false
     */
    @Transactional
    public boolean processReport(long reportId) {
        Optional<DeviceStatusReportRepository.PendingReport> locked = deviceStatusReportRepository.lockPending(reportId);
        if (locked.isEmpty()) {
            return false;   // 그 사이 처리됨
        }
        DeviceStatusReportRepository.PendingReport report = locked.get();

        boolean becameFaulty = false;
        // 등록이 해제된 약통의 보고는 알릴 대상(복약자와 보호자)이 없으니 판정하지 않고 처리 완료로만 표시한다
        if (report.registered()) {
            DeviceStatusReportRepository.PreviousValues previous =
                    deviceStatusReportRepository.previousValues(report.deviceId(), reportId);
            becameFaulty = DeviceFaultRules.becameFaulty(
                    previous.irSensorOk(), previous.buttonOk(), previous.irFaultCompartments(),
                    report.irSensorOk(), report.buttonOk(), report.irFaultCompartments());
            if (becameFaulty) {
                notificationCreator.onDeviceFault(report.patientId(), report.deviceId());
            }
        }
        deviceStatusReportRepository.markProcessed(reportId, OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        return becameFaulty;
    }

    /** 처리를 계속 실패하는 보고를 건너뛴다: 처리 완료로 표시만 한다(원본 보고는 그대로 남는다) */
    @Transactional
    public void discard(long reportId) {
        deviceStatusReportRepository.markProcessed(reportId, OffsetDateTime.now(ApiTime.SERVICE_ZONE));
    }
}