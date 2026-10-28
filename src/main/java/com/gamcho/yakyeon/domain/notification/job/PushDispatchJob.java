package com.gamcho.yakyeon.domain.notification.job;

import com.gamcho.yakyeon.domain.notification.service.PushDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 푸시 발송 정기 작업. 15초마다 새 알림을 보내고, 재시도할 차례가 된 실패를 다시 보낸다.
 * 이전 실행이 끝난 뒤 15초를 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 보낸 건이 있을 때만 건수 로그를 남기고 토큰이나 알림 내용은 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatchJob {

    private final PushDispatchService pushDispatchService;

    @Scheduled(fixedDelay = 15_000, initialDelay = 30_000)
    public void run() {
        try {
            int sent = pushDispatchService.dispatchNew();
            int retried = pushDispatchService.retryDue();
            if (sent > 0 || retried > 0) {
                log.info("푸시 발송: 새로 {}건, 재시도 {}건", sent, retried);
            }
        } catch (RuntimeException e) {
            log.error("푸시 발송 작업 실패: {}", e.getClass().getSimpleName());
        }
    }
}