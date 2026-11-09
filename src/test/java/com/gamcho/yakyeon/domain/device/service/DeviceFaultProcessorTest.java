package com.gamcho.yakyeon.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장된 상태 보고를 처리하는 반복문의 동작(알림 건수 집계, 순서, 실패 격리, 건너뛰기).
 * DB도 가짜 객체 도구도 쓰지 않고, 읽기·처리·건너뛰기 자리를 일반 코드로 채워서 확인한다.
 */
class DeviceFaultProcessorTest {

    private final List<Long> pending = new ArrayList<>();
    private final Map<Long, Queue<BooleanSupplier>> script = new HashMap<>();
    private final List<Long> processCalls = new ArrayList<>();
    private final List<Long> discarded = new ArrayList<>();
    private boolean discardFails = false;

    private DeviceFaultProcessor processor() {
        return new DeviceFaultProcessor(
                limit -> pending,
                reportId -> {
                    processCalls.add(reportId);
                    return script.get(reportId).remove().getAsBoolean();   // 호출할 때마다 정해 둔 동작을 차례로 꺼내 쓴다
                },
                reportId -> {
                    discarded.add(reportId);
                    if (discardFails) {
                        throw new IllegalStateException("건너뛰기 실패");
                    }
                });
    }

    private void reports(long... ids) {
        for (long id : ids) {
            pending.add(id);
        }
    }

    private void script(long reportId, List<BooleanSupplier> steps) {
        script.put(reportId, new ArrayDeque<>(steps));
    }

    private static BooleanSupplier faulty() {
        return () -> true;
    }

    private static BooleanSupplier normal() {
        return () -> false;
    }

    private static BooleanSupplier fails() {
        return () -> {
            throw new IllegalStateException("데이터 문제");
        };
    }

    @Test
    @DisplayName("처리할 보고가 없으면 아무것도 하지 않는다")
    void nothingPending() {
        assertThat(processor().processPending()).isEqualTo(0);
        assertThat(processCalls.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("새 고장이 확인된 보고의 수만 센다 (정상 보고, 이미 처리됐거나 이어지는 고장은 세지 않는다)")
    void countsOnlyNewFaults() {
        reports(1, 2, 3, 4);
        script(1, List.of(normal()));
        script(2, List.of(faulty()));
        script(3, List.of(normal()));
        script(4, List.of(faulty()));
        assertThat(processor().processPending()).isEqualTo(2);
    }

    @Test
    @DisplayName("보고는 읽어 온 순서(오래된 순) 그대로 한 건씩 처리한다")
    void processesInOrder() {
        reports(5, 6, 7);
        script(5, List.of(normal()));
        script(6, List.of(normal()));
        script(7, List.of(normal()));
        processor().processPending();
        assertThat(processCalls.toString()).isEqualTo("[5, 6, 7]");
    }

    @Test
    @DisplayName("한 보고가 실패해도 뒤의 보고는 계속 처리한다 (한 건의 문제가 전체를 막지 않는다)")
    void oneFailureDoesNotBlockTheRest() {
        reports(1, 2, 3);
        script(1, List.of(faulty()));
        script(2, List.of(fails()));
        script(3, List.of(faulty()));
        assertThat(processor().processPending()).as("실패한 2번을 빼고 1번과 3번의 알림").isEqualTo(2);
        assertThat(processCalls.toString()).isEqualTo("[1, 2, 3]");
        assertThat(discarded.isEmpty()).as("한 번 실패했다고 건너뛰지 않는다").isTrue();
    }

    @Test
    @DisplayName("같은 보고가 연속 5번 실패하면 그 보고만 건너뛴다 (4번째까지는 계속 다시 시도)")
    void givesUpAfterFiveConsecutiveFailures() {
        reports(9);
        script(9, List.of(fails(), fails(), fails(), fails(), fails()));
        DeviceFaultProcessor p = processor();
        for (int i = 0; i < 4; i++) {
            p.processPending();
        }
        assertThat(discarded.isEmpty()).as("4번 실패까지는 건너뛰지 않음").isTrue();
        p.processPending();
        assertThat(discarded.toString()).as("5번째 실패에서 건너뜀").isEqualTo("[9]");
    }

    @Test
    @DisplayName("중간에 성공하면 연속 실패 횟수가 처음부터 다시 센다")
    void successResetsTheFailureCount() {
        reports(4);
        script(4, List.of(fails(), fails(), fails(), fails(), normal(), fails(), fails(), fails(), fails()));
        DeviceFaultProcessor p = processor();
        for (int i = 0; i < 9; i++) {
            p.processPending();
        }
        assertThat(discarded.isEmpty()).as("실패 4번, 성공, 실패 4번은 연속 5번이 아니다").isTrue();
    }

    @Test
    @DisplayName("건너뛰기 자체가 실패해도 반복문은 멈추지 않고 다음 보고를 처리한다")
    void discardFailureIsContained() {
        discardFails = true;
        reports(1, 2);
        script(1, List.of(fails(), fails(), fails(), fails(), fails()));
        script(2, List.of(faulty(), faulty(), faulty(), faulty(), faulty()));
        DeviceFaultProcessor p = processor();
        int alerts = 0;
        for (int i = 0; i < 5; i++) {
            alerts += p.processPending();
        }
        assertThat(discarded.toString()).isEqualTo("[1]");
        assertThat(alerts).as("2번 보고는 매번 정상적으로 처리됨").isEqualTo(5);
    }

    @Test
    @DisplayName("건너뛴 뒤 같은 번호가 다시 실패해도 횟수를 처음부터 센다")
    void failureCountRestartsAfterDiscard() {
        reports(3);
        script(3, List.of(fails(), fails(), fails(), fails(), fails(), fails(), fails(), fails(), fails(), fails()));
        DeviceFaultProcessor p = processor();
        for (int i = 0; i < 10; i++) {
            p.processPending();
        }
        assertThat(discarded.toString()).as("5번째와 10번째에 건너뜀").isEqualTo("[3, 3]");
    }
}