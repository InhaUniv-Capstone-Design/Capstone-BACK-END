package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.domain.notification.logic.NotificationKind;
import com.gamcho.yakyeon.domain.notification.logic.NotificationSettingKey;
import com.gamcho.yakyeon.domain.notification.repository.NotificationWriteRepository;
import com.gamcho.yakyeon.domain.notification.repository.NotificationWriteRepository.Links;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사건이 일어났을 때 알림을 만든다. 호출한 쪽의 트랜잭션 안에서 실행되어, 사건이 취소되면 알림도 함께 취소된다.
 * 알림 문구는 일반 문구만 쓰고(NotificationKind), 받는 사람의 알림 설정(꺼짐)을 존중한다.
 * 푸시 발송은 별도 단계(19단계)에서 저장된 알림을 읽어서 처리한다.
 *
 * 받는 사람
 *  복용 감지, 약통 열기만 함, 미복용  → 연동된 보호자 ("보호자 공유 알림" 설정 GUARDIAN_*)
 *  복용 감지, 약통 열기만 함          → 복약자 본인에게도 (설정 DEVICE, 앱의 "약통 상태 알림")
 *  기기 이상                          → 연동된 보호자(GUARDIAN_DEVICE) + 복약자 본인(DEVICE)
 *  약이 얼마 안 남음                  → 복약자 본인 + 연동된 보호자 (끄는 설정 없음)
 *  복용 시간 알림(예정 10분 전)       → 복약자 본인 (DOSE_REMINDER)
 *  미복용 알림(예정 30분 후, 본인용)   → 복약자 본인 (MISSED)
 *  복용 종료 예정(마지막 복용일 3일 전) → 복약자 본인 (END_SOON)
 *  병용금기 새 등재                   → 복약자 본인 (끄는 설정 없음). 보호자는 열람 범위(DUR_INFO) 적용 단계에서 더한다
 */
@Service
@RequiredArgsConstructor
public class NotificationCreator {

    private final NotificationWriteRepository notificationWriteRepository;

    /** 약통에서 복용이 확인됐다 (같은 복용 이벤트에는 한 번만) */
    public int onIntakeDetected(long patientId, long intakeEventId, long mappingId, long deviceId) {
        Links links = new Links(null, intakeEventId, mappingId, deviceId);
        return notificationWriteRepository.insertForGuardians(patientId, NotificationKind.INTAKE_DETECTED,
                NotificationSettingKey.GUARDIAN_TAKEN, links, true)
                + notificationWriteRepository.insertForPatient(patientId, NotificationKind.INTAKE_DETECTED,
                NotificationSettingKey.DEVICE, links, true);
    }

    /** 약통이 열렸지만 복용 확인 버튼이 눌리지 않았다 (같은 이벤트에는 한 번만) */
    public int onDeviceOpened(long patientId, long intakeEventId, long mappingId, long deviceId) {
        Links links = new Links(null, intakeEventId, mappingId, deviceId);
        return notificationWriteRepository.insertForGuardians(patientId, NotificationKind.DEVICE_OPENED,
                NotificationSettingKey.GUARDIAN_OPEN, links, true)
                + notificationWriteRepository.insertForPatient(patientId, NotificationKind.DEVICE_OPENED,
                NotificationSettingKey.DEVICE, links, true);
    }

    /** 예정 시각에서 1시간이 지났는데 복용이 없다 (같은 예정에는 받는 사람당 한 번만) */
    public int onMissedDose(long patientId, long scheduledDoseId) {
        return notificationWriteRepository.insertForGuardians(patientId, NotificationKind.MISSED_DOSE,
                NotificationSettingKey.GUARDIAN_MISSED, new Links(scheduledDoseId, null, null, null), true);
    }

    /** 예정 시각이 10분 뒤로 다가왔다 (같은 예정에는 한 번만) */
    public int onDoseReminder(long patientId, long scheduledDoseId) {
        return notificationWriteRepository.insertForPatient(patientId, NotificationKind.DOSE_REMINDER,
                NotificationSettingKey.DOSE_REMINDER, new Links(scheduledDoseId, null, null, null), true);
    }

    /** 예정 시각에서 30분이 지났는데 복용이 없다: 복약자 본인에게 다시 알린다 (같은 예정에는 한 번만) */
    public int onSelfMissedDose(long patientId, long scheduledDoseId) {
        return notificationWriteRepository.insertForPatient(patientId, NotificationKind.MISSED_DOSE_SELF,
                NotificationSettingKey.MISSED, new Links(scheduledDoseId, null, null, null), true);
    }

    /** 처방의 마지막 복용일이 3일 안으로 다가왔다 (같은 처방에는 DB가 한 번만 허용한다) */
    public int onEndSoon(long patientId, long prescriptionId) {
        return notificationWriteRepository.insertForPatient(patientId, NotificationKind.END_SOON,
                NotificationSettingKey.END_SOON, new Links(null, null, null, null, prescriptionId), false);
    }

    /**
     * 병용금기로 등재된 새 약 조합이 확인됐다 (병용금기 검사 한 번에 한 건, 호출하는 쪽이 새 쌍이 있을 때만 부른다).
     * 검사마다 번호가 달라서 같은 알림이 겹칠 일이 없으므로 중복 확인은 하지 않는다.
     */
    public int onInteractionFound(long patientId, long checkId) {
        return notificationWriteRepository.insertForPatient(patientId, NotificationKind.INTERACTION_INFO, null,
                new Links(null, null, null, null, null, checkId), false);
    }

    /** 약통에 이상이 생겼다 (정상에서 이상으로 바뀌는 순간에만 호출된다) */
    public int onDeviceFault(long patientId, long deviceId) {
        Links links = new Links(null, null, null, deviceId);
        return notificationWriteRepository.insertForGuardians(patientId, NotificationKind.DEVICE_STATUS,
                NotificationSettingKey.GUARDIAN_DEVICE, links, false)
                + notificationWriteRepository.insertForPatient(patientId, NotificationKind.DEVICE_STATUS,
                NotificationSettingKey.DEVICE, links, false);
    }

    /** 약봉지가 기준 이하로 남았다 (호출하는 쪽이 칸마다 한 번만 부르도록 보장한다) */
    public int onLowStock(long patientId, long mappingId, long deviceId) {
        Links links = new Links(null, null, mappingId, deviceId);
        return notificationWriteRepository.insertForPatient(patientId, NotificationKind.LOW_STOCK, null, links, false)
                + notificationWriteRepository.insertForGuardians(patientId, NotificationKind.LOW_STOCK, null, links, false);
    }
}