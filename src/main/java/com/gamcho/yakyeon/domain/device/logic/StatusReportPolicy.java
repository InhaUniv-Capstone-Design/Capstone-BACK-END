package com.gamcho.yakyeon.domain.device.logic;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 기기 상태 보고를 이력(device_status_report)에 남길지 정하는 기준 (스프링 없는 순수 로직).
 * 기기는 1분마다 보고해도 되는데 그대로 다 쌓으면 기기 하나가 하루 1,440줄이라서,
 * "처음이거나, 센서·펌웨어 상태가 바뀌었거나, 마지막 기록 뒤 일정 시간이 지났을 때"만 남긴다.
 * 최신 상태(마지막 접속 시각, 온라인 여부)는 기록 여부와 상관없이 기기 정보에 항상 갱신한다.
 */
public final class StatusReportPolicy {

    private StatusReportPolicy() {
    }

    /** 보고 내용. null은 "이번 보고에 이 항목이 없다"는 뜻이라 바뀐 것으로 보지 않는다 */
    public record Values(Boolean irSensorOk, Boolean buttonOk, String firmwareVersion, List<Integer> irFaultCompartments) {
    }

    public static boolean shouldRecord(Values last, OffsetDateTime lastAt, Values current, OffsetDateTime now, Duration interval) {
        if (last == null || lastAt == null) {
            return true;
        }
        if (!lastAt.plus(interval).isAfter(now)) {
            return true;
        }
        return changed(last.irSensorOk(), current.irSensorOk())
                || changed(last.buttonOk(), current.buttonOk())
                || changed(last.firmwareVersion(), current.firmwareVersion())
                || faultsChanged(last.irFaultCompartments(), current.irFaultCompartments());
    }

    private static <T> boolean changed(T previous, T next) {
        return next != null && !Objects.equals(previous, next);
    }

    /** 고장 칸 목록은 순서와 상관없이 같은 집합이면 같은 것으로 본다. 이번 보고에 항목이 없으면(null) 바뀐 게 아니다 */
    private static boolean faultsChanged(List<Integer> previous, List<Integer> next) {
        if (next == null) {
            return false;
        }
        return !sorted(previous).equals(sorted(next));
    }

    private static List<Integer> sorted(List<Integer> list) {
        List<Integer> copy = new ArrayList<>(list == null ? List.of() : list);
        Collections.sort(copy);
        return copy;
    }
}