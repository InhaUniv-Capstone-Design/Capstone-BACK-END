package com.gamcho.yakyeon.domain.notification.job;

import com.gamcho.yakyeon.domain.notification.service.MissedDoseAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미복용 알림 정기 작업. 1분마다 "예정 시각에서 1시간이 지났는데 복용이 없는" 예정을 찾아 보호자 알림을 만든다.
 * 이전 실행이 끝난 뒤 1분을 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 새로 만든 알림이 있을 때만 건수 로그를 남기고, 복약자나 예정 정보는 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissedDoseAlertJob {

    private final MissedDoseAlertService missedDoseAlertService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void run() {
        try {
            int created = missedDoseAlertService.alertOverdueDoses();
            if (created > 0) {
                log.info("미복용 알림 생성: {}건", created);
            }
        } catch (RuntimeException e) {
            log.error("미복용 알림 생성 실패: {}", e.getMessage());
        }
    }
}