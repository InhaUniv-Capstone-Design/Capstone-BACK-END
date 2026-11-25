package com.gamcho.yakyeon.domain.notification.job;

import com.gamcho.yakyeon.domain.notification.service.EndSoonAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 복용 종료 예정 알림 정기 작업. 서버를 켠 뒤 2분이 지나 한 번, 그 뒤 이전 실행이 끝나고 1시간마다 돈다.
 * 정해진 시각(오전 9시)에만 도는 방식이 아니라 "9시 이후 처음 도는 점검"에서 보내므로, 그 시각에 서버가 꺼져 있었어도
 * 다시 켜진 뒤 같은 날 안에 알림이 나간다. 새로 만든 알림이 있을 때만 건수 로그를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndSoonAlertJob {

    private final EndSoonAlertService endSoonAlertService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void run() {
        try {
            int created = endSoonAlertService.alertEndingPrescriptions();
            if (created > 0) {
                log.info("복용 종료 예정 알림 생성: {}건", created);
            }
        } catch (RuntimeException e) {
            log.error("복용 종료 예정 알림 생성 실패: {}", e.getMessage());
        }
    }
}