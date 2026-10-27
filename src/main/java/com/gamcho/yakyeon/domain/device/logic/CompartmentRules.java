package com.gamcho.yakyeon.domain.device.logic;

/** 약통 칸 배정의 판단 규칙 (스프링·DB 없는 순수 로직) */
public final class CompartmentRules {

    /** 한 번에 충전하거나 추가할 수 있는 약봉지 수의 상한. 잘못 입력한 터무니없는 값을 막는 안전장치다 */
    public static final int MAX_POUCHES_PER_REQUEST = 100;

    private CompartmentRules() {
    }

    /** 칸 번호가 이 약통에 있는 칸인지 (1번부터 약통의 칸 수까지) */
    public static boolean isValidCompartmentNo(int compartmentNo, int maxCompartments) {
        return compartmentNo >= 1 && compartmentNo <= maxCompartments;
    }

    /**
     * 남은 약봉지가 적은지. 충전이 확인되어 쓰고 있는 칸(ACTIVE)만 해당하고,
     * 남은 개수가 기준 이하(기준 포함)면 적은 것으로 본다.
     */
    public static boolean isLowStock(String status, Integer remaining, Integer threshold) {
        return "ACTIVE".equals(status) && remaining != null && threshold != null && remaining <= threshold;
    }
}