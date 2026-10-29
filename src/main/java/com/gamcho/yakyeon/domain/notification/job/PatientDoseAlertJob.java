package com.gamcho.yakyeon.domain.notification.job;

import com.gamcho.yakyeon.domain.notification.service.PatientDoseAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 복약자 본인용 시간 알림 정기 작업. 1분마다 "예정 10분 전" 복용 시간 알림과 "예정 30분 후 미복용" 알림을 만든다.
 * 이전 실행이 끝난 뒤 1분을 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 두 알림은 서로 영향을 주지 않도록 따로 실행하고, 새로 만든 알림이 있을 때만 건수 로그를 남긴다
 * (복약자나 예정 정보는 남기지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientDoseAlertJob {

    private final PatientDoseAlertService patientDoseAlertService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 50_000)
    public void run() {
        try {
            int reminders = patientDoseAlertService.remindUpcomingDoses();
            if (reminders > 0) {
                log.info("복용 시간 알림 생성: {}건", reminders);
            }
        } catch (RuntimeException e) {
            log.error("복용 시간 알림 생성 실패: {}", e.getMessage());
        }
        try {
            int missed = patientDoseAlertService.alertSelfOverdueDoses();
            if (missed > 0) {
                log.info("본인 미복용 알림 생성: {}건", missed);
            }
        } catch (RuntimeException e) {
            log.error("본인 미복용 알림 생성 실패: {}", e.getMessage());
        }
    }
}