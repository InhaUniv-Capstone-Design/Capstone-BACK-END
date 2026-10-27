package com.gamcho.yakyeon.domain.device.job;

import com.gamcho.yakyeon.domain.device.service.DeviceSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 약통 신호 정리 정기 작업. 칸 열림만 오고 허용 시간(기본 60초) 안에 복용 확인 버튼이 오지 않은 신호를
 * "열기만 함(OPEN_ONLY)"으로 확정한다. 10초마다 돌고, 처리할 신호가 있으면 없어질 때까지 이어서 처리한다.
 * 이전 실행이 끝난 뒤 10초를 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 * 처리한 건이 있을 때만 로그를 남기고 신호 내용이나 복약자 정보는 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceSignalJob {

    /** 한 번 실행에서 처리하는 최대 건수. 문제가 생겨 끝없이 도는 것을 막는 안전장치다 */
    private static final int MAX_PER_RUN = 200;

    private final DeviceSignalService deviceSignalService;

    @Scheduled(fixedDelay = 10_000, initialDelay = 20_000)
    public void closeStaleOpenings() {
        int closed = 0;
        try {
            while (closed < MAX_PER_RUN && deviceSignalService.closeOneStaleIr()) {
                closed++;
            }
        } catch (RuntimeException e) {
            log.error("약통 열림 신호 정리 실패: {}", e.getMessage());
        }
        if (closed > 0) {
            log.info("약통 열림 신호 정리: {}건", closed);
        }
    }
}