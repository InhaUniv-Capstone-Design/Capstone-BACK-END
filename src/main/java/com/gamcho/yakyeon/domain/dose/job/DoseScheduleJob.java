package com.gamcho.yakyeon.domain.dose.job;

import com.gamcho.yakyeon.domain.dose.service.DoseScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 복용 일정 정기 점검. 모든 처방의 앞으로 7일치 예정을 맞춘다.
 *  - 매일 00:05(한국 시간): 새로 생긴 하루를 채운다
 *  - 서버가 켜질 때: 서버가 꺼져 있던 동안 빠진 날을 메운다
 * 처방마다 따로 실행해서(각자의 트랜잭션) 한 처방이 실패해도 나머지는 계속한다. 로그에는 번호와 건수만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DoseScheduleJob {

    private final DoseScheduleService doseScheduleService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void nightly() {
        run("자정 점검");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run("서버 시작 점검");
    }

    private void run(String reason) {
        List<Long> ids = doseScheduleService.targetPrescriptionIds();
        int ok = 0;
        int failed = 0;
        int deleted = 0;
        int created = 0;
        for (Long id : ids) {
            try {
                DoseScheduleService.Summary s = doseScheduleService.reconcile(id);
                deleted += s.deleted();
                created += s.created();
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.error("복용 일정 점검 실패 (처방 {}): {}", id, e.getMessage());
            }
        }
        log.info("복용 일정 {}: 처방 {}건 성공, {}건 실패, 예정 삭제 {}건, 생성 {}건", reason, ok, failed, deleted, created);
    }
}