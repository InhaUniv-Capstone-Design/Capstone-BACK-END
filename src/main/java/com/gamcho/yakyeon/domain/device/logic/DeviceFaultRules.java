package com.gamcho.yakyeon.domain.device.logic;

import java.util.List;

/**
 * 약통 이상 알림을 보낼지 정하는 규칙 (스프링·DB 없는 순수 로직).
 * 상태 보고는 1분마다 올 수 있어서 "이상인 상태"가 아니라 "정상에서 이상으로 바뀌는 순간"에만 알린다.
 * 그렇지 않으면 이상이 계속되는 동안 매분 알림이 쌓인다. 이상이 풀린 뒤 다시 생기면 또 알린다.
 */
public final class DeviceFaultRules {

    private DeviceFaultRules() {
    }

    /** IR 센서나 버튼이 고장이거나, 고장 난 칸이 하나라도 있으면 이상이다 (값이 없으면 이상으로 보지 않는다) */
    public static boolean isFaulty(Boolean irSensorOk, Boolean buttonOk, List<Integer> irFaultCompartments) {
        return Boolean.FALSE.equals(irSensorOk)
                || Boolean.FALSE.equals(buttonOk)
                || (irFaultCompartments != null && !irFaultCompartments.isEmpty());
    }

    /**
     * 이번 보고로 정상에서 이상으로 바뀌었는지. 이번 보고에 없는 항목(null)은 이전 값이 그대로라고 본다.
     */
    public static boolean becameFaulty(Boolean prevIrOk, Boolean prevButtonOk, List<Integer> prevFaults,
                                       Boolean newIrOk, Boolean newButtonOk, List<Integer> newFaults) {
        boolean was = isFaulty(prevIrOk, prevButtonOk, prevFaults);
        boolean now = isFaulty(
                newIrOk != null ? newIrOk : prevIrOk,
                newButtonOk != null ? newButtonOk : prevButtonOk,
                newFaults != null ? newFaults : prevFaults);
        return now && !was;
    }
}