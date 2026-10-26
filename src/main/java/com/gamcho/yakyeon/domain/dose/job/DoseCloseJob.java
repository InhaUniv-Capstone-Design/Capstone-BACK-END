package com.gamcho.yakyeon.domain.dose.job;

import com.gamcho.yakyeon.domain.dose.service.DoseCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 누락 자동 확정 정기 작업.
 *  - 1분마다: 마감이 지난 판정 전 예정을 누락으로 확정한다 (마감 후 길어야 1분 안에 반영된다).
 *    판정 전 예정의 마감 시각 인덱스가 있어서 매분 불러도 부담이 거의 없다.
 *  - 서버가 켜질 때: 서버가 꺼져 있던 동안 마감이 지난 예정을 한꺼번에 확정한다.
 * 이전 실행이 끝난 뒤 1분을 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 확정한 건이 있을 때만 로그를 남긴다 (예정 번호나 복약자 정보는 남기지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DoseCloseJob {

    private final DoseCloseService doseCloseService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void everyMinute() {
        run("정기");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run("서버 시작");
    }

    private void run(String reason) {
        try {
            long closed = doseCloseService.closeMissed();
            if (closed > 0) {
                log.info("복용 누락 확정 ({}): {}건", reason, closed);
            }
        } catch (RuntimeException e) {
            log.error("복용 누락 확정 실패 ({}): {}", reason, e.getMessage());
        }
    }
}