package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.notification.logic.MissedDoseRules;
import com.gamcho.yakyeon.domain.notification.repository.NotificationWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 미복용 알림: 예정 시각에서 1시간이 지났는데도 복용이 기록되지 않은 예정을 찾아 연동된 보호자에게 알린다.
 * 누락 확정(예정 3시간 뒤)과는 별개라서, 알림 뒤에 복용이 기록되면 정상적으로 지연으로 판정된다.
 * 1분마다 같은 예정을 다시 만나도 같은 예정에 같은 사람에게는 한 번만 만든다.
 */
@Service
@RequiredArgsConstructor
public class MissedDoseAlertService {

    /** 한 번에 처리하는 최대 예정 수 (문제가 생겨 한없이 늘어나는 것을 막는 안전장치) */
    private static final int BATCH_LIMIT = 500;

    private final NotificationWriteRepository notificationWriteRepository;
    private final NotificationCreator notificationCreator;

    /** @return 이번에 새로 만든 알림 수 */
    @Transactional
    public int alertOverdueDoses() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        List<NotificationWriteRepository.OverdueDose> doses =
                notificationWriteRepository.lockOverdueDoses(MissedDoseRules.cutoff(now), now, BATCH_LIMIT);
        int created = 0;
        for (NotificationWriteRepository.OverdueDose dose : doses) {
            created += notificationCreator.onMissedDose(dose.patientId(), dose.scheduledDoseId());
        }
        return created;
    }
}