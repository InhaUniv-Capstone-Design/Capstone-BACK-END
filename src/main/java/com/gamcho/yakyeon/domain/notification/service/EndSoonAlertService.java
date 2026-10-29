package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.notification.logic.EndSoonRules;
import com.gamcho.yakyeon.domain.notification.repository.NotificationWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 복용 종료 예정 알림: 처방의 마지막 복용일이 3일 안으로 다가오면 복약자 본인에게 한 번 알린다 (끄는 설정 END_SOON).
 * 점검은 1시간마다 돌고 오전 9시 이전에는 아무것도 하지 않는다. 같은 처방을 다시 만나도 DB가 중복을 막는다.
 * 앱 계정이 없는 복약자는 받는 사람이 없어서 알림이 만들어지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class EndSoonAlertService {

    /** 한 번에 처리하는 최대 처방 수 (문제가 생겨 한없이 늘어나는 것을 막는 안전장치) */
    private static final int BATCH_LIMIT = 500;

    private final NotificationWriteRepository notificationWriteRepository;
    private final NotificationCreator notificationCreator;

    /** @return 이번에 새로 만든 알림 수 */
    @Transactional
    public int alertEndingPrescriptions() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        if (!EndSoonRules.isNoticeTime(now.toLocalTime())) {
            return 0;
        }
        LocalDate today = now.toLocalDate();
        List<NotificationWriteRepository.EndingPrescription> prescriptions =
                notificationWriteRepository.findEndingPrescriptions(today, EndSoonRules.noticeUntil(today), BATCH_LIMIT);
        int created = 0;
        for (NotificationWriteRepository.EndingPrescription prescription : prescriptions) {
            created += notificationCreator.onEndSoon(prescription.patientId(), prescription.prescriptionId());
        }
        return created;
    }
}