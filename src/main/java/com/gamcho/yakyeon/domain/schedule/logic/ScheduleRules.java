package com.gamcho.yakyeon.domain.schedule.logic;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 처방별 복용 시각 설정의 규칙 (스프링·DB 없는 순수 로직). 규칙을 바꿀 때는 이 파일의 상수만 고치면 된다.
 *
 * - 약을 먹는 시간대마다 시각이 있어야 한다.
 * - 아침 &lt; 점심 &lt; 저녁 순서여야 한다.
 * - 시간대 사이는 최소 MIN_GAP_MIN분(4시간) 떨어져야 한다 (디자인의 "최소 4시간 이상 간격").
 *   정상 인정 30분 + 마감 180분이 서로 겹치지 않으려면 마감(180분)보다 간격이 커야 한다.
 * - 마감(시각 + 180분)이 자정을 넘으면 안 된다. "하루의 경계"가 팀에서 확정되기 전까지 자정을 넘는 예정은 만들지 않는다.
 */
public final class ScheduleRules {

    /** 정상으로 인정하는 시간(분). DB 기본값과 같다 */
    public static final int NORMAL_WINDOW_MIN = 30;
    /** 이 시간(분)이 지나면 누락으로 확정. DB 기본값과 같다 */
    public static final int CLOSE_AFTER_MIN = 180;
    /** 시간대 사이 최소 간격(분) */
    public static final int MIN_GAP_MIN = 240;

    private static final List<String> ORDER = List.of("MORNING", "LUNCH", "DINNER");
    private static final Map<String, String> LABEL = Map.of("MORNING", "아침", "LUNCH", "점심", "DINNER", "저녁");

    private ScheduleRules() {
    }

    /** 마감이 자정을 넘지 않는 가장 늦은 시각 (180분이면 20:59) */
    public static LocalTime latestDoseTime() {
        return LocalTime.MIDNIGHT.plusMinutes(24 * 60 - CLOSE_AFTER_MIN - 1);
    }

    public static String label(String slot) {
        return LABEL.getOrDefault(slot, slot);
    }

    /**
     * @param times         시간대 이름(MORNING/LUNCH/DINNER) → 복용 시각
     * @param requiredSlots 이 처방의 약이 실제로 쓰는 시간대. 모두 시각이 있어야 한다
     * @return 처음 발견한 규칙 위반 메시지(사용자에게 그대로 보여줄 수 있는 문장). 문제없으면 빈 값
     */
    public static Optional<String> firstViolation(Map<String, LocalTime> times, Set<String> requiredSlots) {
        List<String> missing = new ArrayList<>();
        for (String slot : ORDER) {
            if (requiredSlots.contains(slot) && !times.containsKey(slot)) {
                missing.add(label(slot));
            }
        }
        if (!missing.isEmpty()) {
            return Optional.of(String.join(", ", missing) + " 복용 시각을 입력해주세요.");
        }

        LocalTime previous = null;
        String previousSlot = null;
        for (String slot : ORDER) {
            LocalTime t = times.get(slot);
            if (t == null) {
                continue;
            }
            if (t.isAfter(latestDoseTime())) {
                return Optional.of(label(slot) + " 복용 시각은 " + latestDoseTime() + " 이전으로 설정해주세요.");
            }
            if (previous != null) {
                if (!t.isAfter(previous)) {
                    return Optional.of("아침, 점심, 저녁 순서로 시각을 설정해주세요.");
                }
                int gap = (t.toSecondOfDay() - previous.toSecondOfDay()) / 60;
                if (gap < MIN_GAP_MIN) {
                    // 아침·점심·저녁은 모두 받침이 있는 단어라 항상 "과"가 맞다
                    return Optional.of(label(previousSlot) + "과 " + label(slot) + " 복용 시각은 최소 "
                            + (MIN_GAP_MIN / 60) + "시간 이상 간격을 두어야 합니다.");
                }
            }
            previous = t;
            previousSlot = slot;
        }
        return Optional.empty();
    }
}