package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.notification.logic.DoseAlertRules;
import com.gamcho.yakyeon.domain.notification.repository.NotificationWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 복약자 본인에게 가는 시간 기준 알림.
 *  - 복용 시간 알림: 예정 10분 전 (끄는 설정 DOSE_REMINDER)
 *  - 미복용 알림:    예정 30분 후에도 기록이 없으면 다시 알림 (끄는 설정 MISSED)
 * 1분마다 같은 예정을 다시 만나도 같은 예정에 같은 사람에게는 한 번만 만든다.
 * 앱 계정이 없는 복약자는 받는 사람이 없어서 알림이 만들어지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PatientDoseAlertService {

    /** 한 번에 처리하는 최대 예정 수 (문제가 생겨 한없이 늘어나는 것을 막는 안전장치) */
    private static final int BATCH_LIMIT = 500;

    private final NotificationWriteRepository notificationWriteRepository;
    private final NotificationCreator notificationCreator;

    /** @return 이번에 새로 만든 복용 시간 알림 수 */
    @Transactional
    public int remindUpcomingDoses() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        List<NotificationWriteRepository.UpcomingDose> doses =
                notificationWriteRepository.lockUpcomingDoses(now, DoseAlertRules.reminderUntil(now), BATCH_LIMIT);
        int created = 0;
        for (NotificationWriteRepository.UpcomingDose dose : doses) {
            created += notificationCreator.onDoseReminder(dose.patientId(), dose.scheduledDoseId());
        }
        return created;
    }

    /** @return 이번에 새로 만든 본인 미복용 알림 수 */
    @Transactional
    public int alertSelfOverdueDoses() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        List<NotificationWriteRepository.OverdueDose> doses =
                notificationWriteRepository.lockOverdueDoses(DoseAlertRules.selfMissedCutoff(now), now, BATCH_LIMIT);
        int created = 0;
        for (NotificationWriteRepository.OverdueDose dose : doses) {
            created += notificationCreator.onSelfMissedDose(dose.patientId(), dose.scheduledDoseId());
        }
        return created;
    }
}