package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.device.dto.DeviceSignalRequest;
import com.gamcho.yakyeon.domain.device.dto.DeviceSignalResponse;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.logic.CompartmentRules;
import com.gamcho.yakyeon.domain.device.logic.DoseMatcher;
import com.gamcho.yakyeon.domain.device.logic.SignalPairing;
import com.gamcho.yakyeon.domain.device.repository.DeviceSignalRepository;
import com.gamcho.yakyeon.domain.dose.logic.DoseRecordRules;
import com.gamcho.yakyeon.domain.notification.service.NotificationCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 약통 신호(칸 열림, 복용 확인 버튼)를 받아서 복용으로 확인하고 예정을 자동 판정한다.
 *
 * 처리 순서: 기기 인증 → 신호 저장(같은 번호는 한 번만) → 그 칸의 배정을 잠금 → 열림-버튼 짝짓기 →
 * 먹은 예정 찾기와 정상/지연 판정 → 남은 약봉지 1개 차감 → 복용 이벤트 저장(예정 판정은 DB 규칙이 반영).
 *
 *  - 버튼은 받는 즉시 처리한다: 허용 시간 안에 열림이 있으면 CONFIRMED, 없으면 BUTTON_ONLY.
 *  - 열림은 버튼을 기다린다. 허용 시간 안에 버튼이 오면 그 버튼이 짝으로 처리하고,
 *    끝내 안 오면 정기 작업(closeOneStaleIr)이 "열기만 함(OPEN_ONLY)"으로 기록한다.
 *  - 복용을 확인한 직후 허용 시간 안의 반복 버튼, 손을 빼다가 생긴 열림은 같은 복용으로 보고 무시한다.
 *  - 이 칸에 충전이 확인된 약봉지가 없거나 충전 전에 온 신호는 저장만 하고 무시한다.
 */
@Service
@RequiredArgsConstructor
public class DeviceSignalService {

    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(60);

    private final DeviceAuthService deviceAuthService;
    private final DeviceSignalRepository signalRepository;
    private final NotificationCreator notificationCreator;

    @Transactional
    public DeviceSignalResponse receive(String serialNo, String secret, DeviceSignalRequest request) {
        Device device = deviceAuthService.authenticate(serialNo, secret);
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        device.markSeen(now, null, null, null);   // 신호도 "살아 있다"는 증거다

        String clockSource = request.getClockSource() == null ? "NTP" : request.getClockSource();
        // DB 규칙과 같다: 가동 시간은 시계 출처가 UPTIME일 때만 있어야 한다
        if ("UPTIME".equals(clockSource) != (request.getDeviceUptimeMs() != null)) {
            throw new BusinessException(ErrorCode.INVALID_SIGNAL, "기기 가동 시간은 시계 출처가 UPTIME일 때만 보내야 합니다.");
        }
        int compartmentNo = request.getCompartmentNo();
        if (!CompartmentRules.isValidCompartmentNo(compartmentNo, device.getMaxCompartments())) {
            throw new BusinessException(ErrorCode.INVALID_COMPARTMENT_NO);
        }

        OffsetDateTime occurredAt = SignalPairing.clampFuture(request.getOccurredAt(), now);
        Optional<Long> saved = signalRepository.insertSignal(device.getDeviceId(), request.getDeviceEventId(),
                request.getSignalType(), compartmentNo, occurredAt, clockSource, request.getDeviceUptimeMs(), now);
        if (saved.isEmpty()) {
            return DeviceSignalResponse.of(null, "DUPLICATE");
        }
        long signalId = saved.get();
        Duration window = windowOf(device);

        Optional<DeviceSignalRepository.LiveMapping> mapping =
                signalRepository.lockActiveMapping(device.getDeviceId(), compartmentNo);
        if (mapping.isEmpty() || mapping.get().validFrom() == null || occurredAt.isBefore(mapping.get().validFrom())) {
            signalRepository.markProcessed(List.of(signalId), now);
            return DeviceSignalResponse.of(signalId, "IGNORED_NO_ACTIVE_MAPPING");
        }

        Optional<OffsetDateTime> lastConfirmed = signalRepository.lastConfirmedAt(device.getDeviceId(), compartmentNo);
        if ("BUTTON_PRESS".equals(request.getSignalType())) {
            return handleButton(device, mapping.get(), compartmentNo, signalId, occurredAt, lastConfirmed.orElse(null), window, now);
        }
        return handleOpen(signalId, occurredAt, lastConfirmed.orElse(null), window, now);
    }

    /** 열림: 복용 직후의 열림이면 무시하고, 아니면 버튼을 기다린다 (처리 완료 표시를 하지 않는다) */
    private DeviceSignalResponse handleOpen(long signalId, OffsetDateTime occurredAt, OffsetDateTime lastConfirmed,
                                            Duration window, OffsetDateTime now) {
        if (SignalPairing.isWithinWindowAfter(lastConfirmed, occurredAt, window)) {
            signalRepository.markProcessed(List.of(signalId), now);
            return DeviceSignalResponse.of(signalId, "IGNORED_AFTER_TAKEN");
        }
        return DeviceSignalResponse.of(signalId, "WAITING_FOR_BUTTON");
    }

    /** 버튼: 허용 시간 안의 열림과 짝이 되면 CONFIRMED, 아니면 BUTTON_ONLY. 반복 누름은 무시한다 */
    private DeviceSignalResponse handleButton(Device device, DeviceSignalRepository.LiveMapping mapping,
                                              int compartmentNo, long signalId,
                                              OffsetDateTime pressedAt, OffsetDateTime lastConfirmed,
                                              Duration window, OffsetDateTime now) {
        if (SignalPairing.isWithinWindowAfter(lastConfirmed, pressedAt, window)) {
            signalRepository.markProcessed(List.of(signalId), now);
            return DeviceSignalResponse.of(signalId, "IGNORED_REPEATED_PRESS");
        }

        List<SignalPairing.Sig> openings = signalRepository.lockUnprocessedIr(
                device.getDeviceId(), compartmentNo, pressedAt.minus(window), pressedAt);
        Optional<SignalPairing.IrMatch> match = SignalPairing.matchIrForButton(pressedAt, openings, window);

        List<Long> consumed = new ArrayList<>();
        consumed.add(signalId);
        Long irSignalId = null;
        OffsetDateTime openedAt = pressedAt;
        String detection = "BUTTON_ONLY";
        if (match.isPresent()) {
            detection = "CONFIRMED";
            irSignalId = match.get().primary().signalId();
            openedAt = match.get().primary().occurredAt();
            match.get().all().forEach(s -> consumed.add(s.signalId()));
        }

        // 먹은 예정을 찾는다. 후보가 없으면 예정 밖(UNSCHEDULED)이고, 약봉지는 하나 먹은 것이므로 개수는 줄인다
        List<DoseMatcher.Candidate> candidates = signalRepository.lockDoseCandidates(
                mapping.prescriptionId(), mapping.patientId(), pressedAt,
                pressedAt.plus(DoseRecordRules.EARLIEST_BEFORE_SCHEDULED));
        Optional<DoseMatcher.Match> dose = DoseMatcher.choose(candidates, pressedAt, DoseRecordRules.EARLIEST_BEFORE_SCHEDULED);
        String judgement = dose.map(DoseMatcher.Match::judgement).orElse("UNSCHEDULED");
        int remaining = signalRepository.decrementRemaining(mapping.mappingId());

        long intakeEventId = signalRepository.insertIntakeEvent(mapping.patientId(), device.getDeviceId(), mapping.mappingId(),
                compartmentNo, mapping.prescriptionId(),
                irSignalId, signalId, detection, openedAt, pressedAt,
                dose.map(DoseMatcher.Match::doseId).orElse(null), dose.map(DoseMatcher.Match::slot).orElse(null),
                judgement, remaining);
        signalRepository.markProcessed(consumed, now);

        // 알림: 복용 확인은 보호자에게, 약봉지가 기준 이하로 줄었으면(칸마다 한 번) 복약자와 보호자에게
        notificationCreator.onIntakeDetected(mapping.patientId(), intakeEventId, mapping.mappingId(), device.getDeviceId());
        if (signalRepository.markLowStockNotifiedIfNeeded(mapping.mappingId())) {
            notificationCreator.onLowStock(mapping.patientId(), mapping.mappingId(), device.getDeviceId());
        }
        return new DeviceSignalResponse(signalId, detection, judgement,
                dose.map(DoseMatcher.Match::doseId).orElse(null), remaining);
    }

    /**
     * 정기 작업: 열림만 오고 허용 시간 안에 버튼이 오지 않은 신호 한 건을 "열기만 함(OPEN_ONLY)"으로 확정한다.
     * 같은 열림 무리(첫 열림부터 허용 시간 안의 열림들)는 한꺼번에 소비해서, 손을 여러 번 넣었다 빼도 이벤트는 하나만 남긴다.
     *
     * @return 처리할 신호가 있었는지 (있으면 계속 부른다)
     */
    @Transactional
    public boolean closeOneStaleIr() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        Optional<DeviceSignalRepository.StaleIr> stale = signalRepository.findOldestStaleIr(now);
        if (stale.isEmpty()) {
            return false;
        }
        DeviceSignalRepository.StaleIr s = stale.get();
        Duration window = Duration.ofSeconds(s.windowSec());

        // 잠금 순서는 수신 처리와 같다: 칸 배정 → 신호
        Optional<DeviceSignalRepository.LiveMapping> mapping =
                signalRepository.lockActiveMapping(s.deviceId(), s.compartmentNo());
        Optional<DeviceSignalRepository.IrRow> ir = signalRepository.lockIrIfUnprocessed(s.signalId());
        if (ir.isEmpty()) {
            return true;   // 그 사이 버튼과 짝이 되어 이미 처리됨
        }
        OffsetDateTime openedAt = ir.get().occurredAt();
        if (mapping.isEmpty() || mapping.get().validFrom() == null || openedAt.isBefore(mapping.get().validFrom())) {
            signalRepository.markProcessed(List.of(s.signalId()), now);
            return true;
        }

        List<Long> group = new ArrayList<>();
        signalRepository.lockUnprocessedIr(s.deviceId(), s.compartmentNo(), openedAt, openedAt.plus(window))
                .forEach(sig -> group.add(sig.signalId()));
        Optional<OffsetDateTime> lastConfirmed = signalRepository.lastConfirmedAt(s.deviceId(), s.compartmentNo());
        if (!SignalPairing.isWithinWindowAfter(lastConfirmed.orElse(null), openedAt, window)) {
            long intakeEventId = signalRepository.insertIntakeEvent(mapping.get().patientId(), s.deviceId(),
                    mapping.get().mappingId(), s.compartmentNo(), mapping.get().prescriptionId(), s.signalId(), null,
                    "OPEN_ONLY", openedAt, null, null, null, "OPEN_ONLY", null);
            notificationCreator.onDeviceOpened(mapping.get().patientId(), intakeEventId, mapping.get().mappingId(), s.deviceId());
        }
        signalRepository.markProcessed(group.isEmpty() ? List.of(s.signalId()) : group, now);
        return true;
    }

    private static Duration windowOf(Device device) {
        return device.getPairWindowSec() == null ? DEFAULT_WINDOW : Duration.ofSeconds(device.getPairWindowSec());
    }
}