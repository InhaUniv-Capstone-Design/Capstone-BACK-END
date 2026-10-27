package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.device.dto.DeviceHeartbeatRequest;
import com.gamcho.yakyeon.domain.device.dto.DeviceHeartbeatResponse;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.logic.DeviceFaultRules;
import com.gamcho.yakyeon.domain.device.logic.StatusReportPolicy;
import com.gamcho.yakyeon.domain.device.repository.DeviceStatusReportRepository;
import com.gamcho.yakyeon.domain.notification.service.NotificationCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 약통의 상태 보고(heartbeat) 처리. 기기가 살아 있다는 신호이면서 센서·펌웨어 상태를 알려 준다.
 * 마지막 접속 시각과 온라인 여부는 매번 갱신하고, 이력 테이블에는 바뀌었거나 1시간이 지났을 때만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class DeviceStatusService {

    /** 상태가 바뀌지 않았어도 이 시간마다 한 번은 이력에 남긴다 */
    private static final Duration REPORT_INTERVAL = Duration.ofHours(1);

    private final DeviceAuthService deviceAuthService;
    private final DeviceStatusReportRepository deviceStatusReportRepository;
    private final NotificationCreator notificationCreator;

    @Transactional
    public DeviceHeartbeatResponse heartbeat(String serialNo, String secret, DeviceHeartbeatRequest request) {
        Device device = deviceAuthService.authenticate(serialNo, secret);
        DeviceHeartbeatRequest r = request == null ? new DeviceHeartbeatRequest() : request;
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);

        // 이상 알림은 "정상에서 이상으로 바뀌는 순간"에만 보내서, 이전 상태를 갱신하기 전에 붙잡아 둔다
        Boolean previousIrOk = device.getIrSensorOk();
        Boolean previousButtonOk = device.getButtonOk();

        device.markSeen(now, r.getFirmwareVersion(), r.getIrSensorOk(), r.getButtonOk());
        device.reportScheduleSynced(r.getScheduleSyncedVer(), now);

        StatusReportPolicy.Values current = new StatusReportPolicy.Values(
                r.getIrSensorOk(), r.getButtonOk(), r.getFirmwareVersion(), r.getIrFaultCompartments());
        Optional<DeviceStatusReportRepository.Last> last = deviceStatusReportRepository.findLast(device.getDeviceId());
        if (DeviceFaultRules.becameFaulty(previousIrOk, previousButtonOk,
                last.map(l -> l.values().irFaultCompartments()).orElse(null),
                r.getIrSensorOk(), r.getButtonOk(), r.getIrFaultCompartments())) {
            notificationCreator.onDeviceFault(device.getPatientId(), device.getDeviceId());
        }
        if (StatusReportPolicy.shouldRecord(
                last.map(DeviceStatusReportRepository.Last::values).orElse(null),
                last.map(DeviceStatusReportRepository.Last::reportedAt).orElse(null),
                current, now, REPORT_INTERVAL)) {
            deviceStatusReportRepository.insert(device.getDeviceId(), now, current, r.getWifiRssi());
        }
        return new DeviceHeartbeatResponse(now, device.getScheduleVersion());
    }
}