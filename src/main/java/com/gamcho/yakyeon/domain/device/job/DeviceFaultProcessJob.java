package com.gamcho.yakyeon.domain.device.job;

import com.gamcho.yakyeon.domain.device.service.DeviceFaultProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 약통 고장 알림 정기 작업. 5초마다 중계 서버가 DB에 저장해 둔 상태 보고를 읽어 새로 생긴 고장을 알린다.
 * 이전 실행이 끝난 뒤 5초를 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 고장 알림을 만든 보고가 있을 때만 건수 로그를 남기고, 보고 내용이나 복약자 정보는 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceFaultProcessJob {

    private final DeviceFaultProcessor processor;

    @Scheduled(fixedDelay = 5_000, initialDelay = 27_000)
    public void run() {
        try {
            int alerts = processor.processPending();
            if (alerts > 0) {
                log.info("약통 고장 알림: {}건", alerts);
            }
        } catch (RuntimeException e) {
            log.error("약통 고장 알림 처리 실패: {}", e.getMessage());
        }
    }
}