package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.domain.device.dto.DeviceSignalResponse;
import com.gamcho.yakyeon.domain.device.repository.DeviceSignalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * 중계 서버가 DB(device_signal)에 저장해 둔 약통 신호를 읽어 복용으로 판정한다.
 *
 * 약통은 MQTT로 중계 서버에 신호를 보내고, 중계 서버는 저장만 한다(processed_at 비워 둠). 복용 판정은 백엔드 몫이라
 * 이 처리기가 발생 시각 순으로 한 건씩 처리한다. 판정 규칙은 약통이 HTTPS로 직접 보낸 신호와 같다(DeviceSignalService).
 *
 * 한 신호가 계속 실패하면(예: 데이터 문제) 그 신호가 뒤의 신호 처리를 매번 막지 않도록, 연속 {@value #GIVE_UP_AFTER}번 실패하면
 * 그 신호만 처리 완료로 표시하고 건너뛴다(원본 신호는 그대로 남는다). 한 신호의 실패는 다른 신호 처리에 영향을 주지 않는다.
 *
 * 읽기·처리·건너뛰기를 함수로 받는 이유: 테스트에서 DB와 가짜 객체 도구 없이 이 반복문의 동작만 확인하려는 것이다.
 * 스프링은 아래 @Autowired 생성자로 실제 저장소와 서비스(트랜잭션이 걸린 것)를 연결한다.
 */
@Slf4j
@Component
public class DeviceSignalProcessor {

    /** 한 번에 읽는 최대 신호 수 (문제가 생겨 끝없이 늘어나는 것을 막는 안전장치) */
    static final int BATCH_LIMIT = 100;

    /** 같은 신호가 이만큼 연속으로 실패하면 건너뛴다 */
    static final int GIVE_UP_AFTER = 5;

    private static final String WAITING = "WAITING_FOR_BUTTON";

    private final IntFunction<List<DeviceSignalRepository.StoredSignal>> findPending;
    private final LongFunction<Optional<DeviceSignalResponse>> process;
    private final LongConsumer discard;

    /** 신호 번호 -> 연속 실패 횟수. 성공하거나 건너뛰면 지운다 */
    private final Map<Long, Integer> failures = new ConcurrentHashMap<>();

    @Autowired
    public DeviceSignalProcessor(DeviceSignalRepository signalRepository, DeviceSignalService signalService) {
        this(signalRepository::findUnprocessed, signalService::processStored, signalService::discard);
    }

    /** 테스트용: 읽기, 한 건 처리, 건너뛰기를 직접 정해 준다 */
    DeviceSignalProcessor(IntFunction<List<DeviceSignalRepository.StoredSignal>> findPending,
                          LongFunction<Optional<DeviceSignalResponse>> process,
                          LongConsumer discard) {
        this.findPending = findPending;
        this.process = process;
        this.discard = discard;
    }

    /**
     * @return 이번에 복용 확인이나 무시로 "결과가 정해진" 신호 수. 버튼을 기다리는 중인 열림 신호는 세지 않는다
     */
    public int processPending() {
        int decided = 0;
        for (DeviceSignalRepository.StoredSignal signal : findPending.apply(BATCH_LIMIT)) {
            try {
                Optional<DeviceSignalResponse> result = process.apply(signal.signalId());
                failures.remove(signal.signalId());
                if (result.isPresent() && !WAITING.equals(result.get().outcome())) {
                    decided++;
                }
            } catch (RuntimeException e) {
                handleFailure(signal.signalId(), e);
            }
        }
        return decided;
    }

    private void handleFailure(long signalId, RuntimeException e) {
        int count = failures.merge(signalId, 1, Integer::sum);
        if (count < GIVE_UP_AFTER) {
            log.warn("약통 신호 처리 실패({}/{}): signal_id={}, {}", count, GIVE_UP_AFTER, signalId, e.getMessage());
            return;
        }
        failures.remove(signalId);
        try {
            discard.accept(signalId);
            log.error("약통 신호 처리를 {}번 연속 실패해서 건너뜁니다: signal_id={}, {}", GIVE_UP_AFTER, signalId, e.getMessage());
        } catch (RuntimeException discardError) {
            log.error("약통 신호를 건너뛰는 것도 실패했습니다: signal_id={}, {}", signalId, discardError.getMessage());
        }
    }
}