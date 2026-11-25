package com.gamcho.yakyeon.domain.account.job;

import com.gamcho.yakyeon.domain.account.service.WithdrawnPurgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 탈퇴 데이터 파기 정기 작업 (10분마다). 지울 것이 있을 때만 로그를 남기고, 로그에는 건수만 쓴다.
 * 이전 실행이 끝난 뒤 10분을 기다리는 방식(fixedDelay)이라 실행이 겹치지 않는다.
 *
 * 설정 (application.yml, 환경변수로도 바꿀 수 있다):
 *  privacy.purge.enabled        false면 아무것도 하지 않는다 (기본 true)
 *  privacy.purge.dry-run        true면 지우지 않고 "지울 건수"만 로그에 남긴다. 처음 배포할 때 건수를 확인하는 용도 (기본 false)
 *
 * 예외 메시지는 로그에 남기지 않는다(삭제 오류 메시지에 행 내용이 섞일 수 있어서). 예외 종류만 남긴다.
 */
@Slf4j
@Component
public class WithdrawnPurgeJob {

    private final WithdrawnPurgeService purgeService;
    private final boolean enabled;
    private final boolean dryRun;

    public WithdrawnPurgeJob(WithdrawnPurgeService purgeService,
                             @Value("${privacy.purge.enabled:true}") boolean enabled,
                             @Value("${privacy.purge.dry-run:false}") boolean dryRun) {
        this.purgeService = purgeService;
        this.enabled = enabled;
        this.dryRun = dryRun;
        if (!enabled) {
            log.warn("탈퇴 데이터 파기 작업이 꺼져 있습니다 (privacy.purge.enabled=false). 탈퇴한 사람의 건강기록이 삭제되지 않습니다.");
        } else if (dryRun) {
            log.warn("탈퇴 데이터 파기: 시험 모드입니다 (privacy.purge.dry-run=true). 아무것도 삭제하지 않고 건수만 기록합니다.");
        }
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    public void everyTenMinutes() {
        if (!enabled) {
            return;
        }
        try {
            WithdrawnPurgeService.Result r = purgeService.run(OffsetDateTime.now(), dryRun);
            if (!r.skipped() && !r.nothing()) {
                log.warn("탈퇴 데이터 파기{}: 관리자 없는 복약자 정리 {}명, 복약자 프로필 {}건, 복약자 계정 {}건, 대리인 계정 {}건",
                        r.dryRun() ? " (시험 모드, 지우지 않음: 지울 건수)" : "",
                        r.abandonedPatients(), r.patients(), r.patientUsers(), r.guardianUsers());
            }
        } catch (RuntimeException e) {
            log.error("탈퇴 데이터 파기 실패: {}", e.getClass().getSimpleName());
        }
    }
}