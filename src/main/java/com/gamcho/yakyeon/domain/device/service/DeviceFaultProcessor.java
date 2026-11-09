package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.domain.device.repository.DeviceStatusReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * 중계 서버가 DB(device_status_report)에 저장해 둔 약통 상태 보고를 읽어 고장 알림을 만든다.
 *
 * 약통은 MQTT로 중계 서버에 상태를 보내고, 중계 서버는 저장만 한다(processed_at 비워 둠). 고장 판정과 알림은 백엔드 몫이라
 * 이 처리기가 오래된 순으로 한 건씩 처리한다(약통 신호를 처리하는 DeviceSignalProcessor와 같은 방식).
 * 서버가 꺼져 있던 동안 쌓인 보고도 켜지면 순서대로 처리한다.
 *
 * 한 보고가 계속 실패하면(예: 데이터 문제) 그 보고가 뒤의 보고 처리를 매번 막지 않도록, 연속 {@value #GIVE_UP_AFTER}번 실패하면
 * 그 보고만 처리 완료로 표시하고 건너뛴다(원본 보고는 그대로 남는다). 한 보고의 실패는 다른 보고 처리에 영향을 주지 않는다.
 *
 * 읽기·처리·건너뛰기를 함수로 받는 이유: 테스트에서 DB 없이 이 반복문의 동작만 확인하려는 것이다.
 */
@Slf4j
@Component
public class DeviceFaultProcessor {

    /** 한 번에 읽는 최대 보고 수 (문제가 생겨 끝없이 늘어나는 것을 막는 안전장치) */
    static final int BATCH_LIMIT = 100;

    /** 같은 보고가 이만큼 연속으로 실패하면 건너뛴다 */
    static final int GIVE_UP_AFTER = 5;

    private final IntFunction<List<Long>> findPending;
    private final LongPredicate process;
    private final LongConsumer discard;

    /** 보고 번호 -> 연속 실패 횟수. 성공하거나 건너뛰면 지운다 */
    private final Map<Long, Integer> failures = new ConcurrentHashMap<>();

    @Autowired
    public DeviceFaultProcessor(DeviceStatusReportRepository reportRepository, DeviceFaultReportService reportService) {
        this(reportRepository::findPendingIds, reportService::processReport, reportService::discard);
    }

    /** 테스트용: 읽기, 한 건 처리(새 고장이면 true), 건너뛰기를 직접 정해 준다 */
    DeviceFaultProcessor(IntFunction<List<Long>> findPending, LongPredicate process, LongConsumer discard) {
        this.findPending = findPending;
        this.process = process;
        this.discard = discard;
    }

    /**
     * @return 이번에 새 고장이 확인되어 알림을 만든 보고 수
     */
    public int processPending() {
        int alerts = 0;
        for (long reportId : findPending.apply(BATCH_LIMIT)) {
            try {
                boolean becameFaulty = process.test(reportId);
                failures.remove(reportId);
                if (becameFaulty) {
                    alerts++;
                }
            } catch (RuntimeException e) {
                handleFailure(reportId, e);
            }
        }
        return alerts;
    }

    private void handleFailure(long reportId, RuntimeException e) {
        int count = failures.merge(reportId, 1, Integer::sum);
        if (count < GIVE_UP_AFTER) {
            log.warn("약통 상태 보고 처리 실패({}/{}): report_id={}, {}", count, GIVE_UP_AFTER, reportId, e.getMessage());
            return;
        }
        failures.remove(reportId);
        try {
            discard.accept(reportId);
            log.error("약통 상태 보고 처리를 {}번 연속 실패해서 건너뜁니다: report_id={}, {}", GIVE_UP_AFTER, reportId, e.getMessage());
        } catch (RuntimeException discardError) {
            log.error("약통 상태 보고를 건너뛰는 것도 실패했습니다: report_id={}, {}", reportId, discardError.getMessage());
        }
    }
}