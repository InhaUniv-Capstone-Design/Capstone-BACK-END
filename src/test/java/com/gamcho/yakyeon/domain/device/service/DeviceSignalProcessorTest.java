package com.gamcho.yakyeon.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.device.dto.DeviceSignalResponse;
import com.gamcho.yakyeon.domain.device.repository.DeviceSignalRepository;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장된 신호를 처리하는 반복문의 동작(건수 집계, 실패 격리, 건너뛰기).
 * DB도 가짜 객체 도구도 쓰지 않고, 읽기·처리·건너뛰기 자리를 일반 코드로 채워서 확인한다.
 */
class DeviceSignalProcessorTest {

    private final List<DeviceSignalRepository.StoredSignal> pending = new ArrayList<>();
    private final Map<Long, Queue<Supplier<Optional<DeviceSignalResponse>>>> script = new HashMap<>();
    private final List<Long> processCalls = new ArrayList<>();
    private final List<Long> discarded = new ArrayList<>();
    private boolean discardFails = false;

    private DeviceSignalProcessor processor() {
        return new DeviceSignalProcessor(
                limit -> pending,
                signalId -> {
                    processCalls.add(signalId);
                    return script.get(signalId).remove().get();   // 호출할 때마다 정해 둔 동작을 차례로 꺼내 쓴다
                },
                signalId -> {
                    discarded.add(signalId);
                    if (discardFails) {
                        throw new IllegalStateException("건너뛰기 실패");
                    }
                });
    }

    private void signals(long... ids) {
        for (long id : ids) {
            pending.add(new DeviceSignalRepository.StoredSignal(id, 1L, "BUTTON_PRESS", 1, OffsetDateTime.now()));
        }
    }

    private void script(long signalId, List<Supplier<Optional<DeviceSignalResponse>>> steps) {
        script.put(signalId, new ArrayDeque<>(steps));
    }

    private static Supplier<Optional<DeviceSignalResponse>> fails() {
        return () -> {
            throw new IllegalStateException("데이터 문제");
        };
    }

    private static Supplier<Optional<DeviceSignalResponse>> result(long signalId, String outcome) {
        return () -> Optional.of(DeviceSignalResponse.of(signalId, outcome));
    }

    private static Supplier<Optional<DeviceSignalResponse>> alreadyProcessedElsewhere() {
        return Optional::empty;
    }

    @Test
    @DisplayName("결과가 정해진 신호만 센다 (버튼을 기다리는 열림, 다른 곳에서 이미 처리된 신호는 세지 않는다)")
    void countsOnlyDecidedSignals() {
        signals(1, 2, 3);
        script(1, List.of(result(1, "WAITING_FOR_BUTTON")));
        script(2, List.of(result(2, "CONFIRMED")));
        script(3, List.of(alreadyProcessedElsewhere()));

        assertThat(processor().processPending()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 신호가 실패해도 뒤의 신호는 계속 처리한다")
    void oneFailureDoesNotBlockOthers() {
        signals(7, 8);
        script(7, List.of(fails()));
        script(8, List.of(result(8, "CONFIRMED")));

        assertThat(processor().processPending()).isEqualTo(1);
        assertThat(processCalls.contains(8L)).isTrue();
    }

    @Test
    @DisplayName("같은 신호가 연속 5번 실패하면 그 신호만 건너뛴다")
    void givesUpAfterFiveConsecutiveFailures() {
        signals(7);
        script(7, Collections.nCopies(5, fails()));
        DeviceSignalProcessor processor = processor();

        for (int i = 0; i < 4; i++) {
            processor.processPending();
        }
        assertThat(discarded.isEmpty()).as("4번째까지는 건너뛰지 않는다").isTrue();

        processor.processPending();   // 다섯 번째
        assertThat(discarded.size()).isEqualTo(1);
        assertThat(discarded.get(0)).isEqualTo(7L);
    }

    @Test
    @DisplayName("중간에 성공하면 실패 횟수가 처음부터 다시 시작한다")
    void successResetsFailureCount() {
        signals(7);
        List<Supplier<Optional<DeviceSignalResponse>>> steps = new ArrayList<>(Collections.nCopies(4, fails()));
        steps.add(result(7, "WAITING_FOR_BUTTON"));
        steps.addAll(Collections.nCopies(4, fails()));
        script(7, steps);
        DeviceSignalProcessor processor = processor();

        for (int i = 0; i < 9; i++) {
            processor.processPending();
        }
        assertThat(discarded.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("건너뛰기 자체가 실패해도 처리기는 멈추지 않는다")
    void discardFailureDoesNotStopProcessing() {
        discardFails = true;
        signals(7);
        script(7, Collections.nCopies(6, fails()));
        DeviceSignalProcessor processor = processor();

        for (int i = 0; i < 6; i++) {
            processor.processPending();   // 예외가 밖으로 나오면 테스트가 실패한다
        }
        assertThat(discarded.size()).as("건너뛰기는 다섯 번째 실패에서 한 번만 시도한다").isEqualTo(1);
    }

    @Test
    @DisplayName("한 번에 읽는 신호는 100건까지다")
    void batchLimitIsPassedToTheSource() {
        List<Integer> asked = new ArrayList<>();
        DeviceSignalProcessor processor = new DeviceSignalProcessor(
                limit -> {
                    asked.add(limit);
                    return List.of();
                },
                signalId -> Optional.empty(),
                signalId -> { });

        processor.processPending();

        assertThat(asked.size()).isEqualTo(1);
        assertThat(asked.get(0)).isEqualTo(100);
    }
}