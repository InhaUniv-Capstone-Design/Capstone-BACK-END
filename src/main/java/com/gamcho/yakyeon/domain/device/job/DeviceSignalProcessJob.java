package com.gamcho.yakyeon.domain.device.job;

import com.gamcho.yakyeon.domain.device.service.DeviceSignalProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 약통 신호 처리 정기 작업. 2초마다 중계 서버가 DB에 저장해 둔 신호를 읽어 복용으로 판정한다
 * (버튼을 누른 뒤 앱에 복용이 반영되기까지 몇 초면 되게 한다).
 * 이전 실행이 끝난 뒤 2초를 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 결과가 정해진 신호가 있을 때만 건수 로그를 남기고, 신호 내용이나 복약자 정보는 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceSignalProcessJob {

    private final DeviceSignalProcessor processor;

    @Scheduled(fixedDelay = 2_000, initialDelay = 22_000)
    public void run() {
        try {
            int decided = processor.processPending();
            if (decided > 0) {
                log.info("약통 신호 처리: {}건", decided);
            }
        } catch (RuntimeException e) {
            log.error("약통 신호 처리 실패: {}", e.getMessage());
        }
    }
}