package com.gamcho.yakyeon.domain.account.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 문자 인증에 대해 OCTOMO에 "문자가 왔나요?"라고 묻는 간격을 제한한다 (스프링·DB 없는 순수 로직).
 *
 * 앱은 사용자가 문자를 보내는 동안 2초 정도마다 인증 확인을 반복해서 부른다. 그 요청마다 OCTOMO를 부르면
 * 하루 호출 한도(octomo.daily-call-limit)를 금방 쓴다. 그래서 같은 인증은 최소 간격(기본 1.5초) 안에는 OCTOMO를 다시
 * 부르지 않고 "아직"이라고만 답한다. 앱이 2초 간격이면 영향이 없고, 더 잦게 부르는 앱만 걸러진다.
 *
 * 한 인증의 요청은 호출하는 쪽에서 행 락으로 직렬화되므로 같은 인증에 대한 동시 호출은 없다.
 */
public final class OctomoCheckThrottle {

    public static final long DEFAULT_MIN_INTERVAL_MILLIS = 1500;
    private static final int PRUNE_THRESHOLD = 5_000;

    private final long minIntervalMillis;
    private final long keepMillis;
    private final Map<Long, Long> lastAsked = new ConcurrentHashMap<>();

    /**
     * @param minIntervalMillis 같은 인증에 OCTOMO를 다시 묻기까지 최소 간격
     * @param keepMillis        이 시간이 지난 기록은 정리 대상 (인증번호 유효시간 이상으로)
     */
    public OctomoCheckThrottle(long minIntervalMillis, long keepMillis) {
        this.minIntervalMillis = minIntervalMillis;
        this.keepMillis = keepMillis;
    }

    /**
     * 지금 OCTOMO를 불러도 되는가. 되면 false(너무 이르지 않음)를 돌려주고 이번 시각을 기록한다.
     * 너무 이르면 true를 돌려주고 기록하지 않는다(간격이 계속 밀리지 않게).
     */
    public boolean tooSoon(long verificationId, long nowMillis) {
        Long last = lastAsked.get(verificationId);
        if (last != null && nowMillis - last < minIntervalMillis) {
            return true;
        }
        if (lastAsked.size() > PRUNE_THRESHOLD) {
            lastAsked.values().removeIf(t -> nowMillis - t > keepMillis);
        }
        lastAsked.put(verificationId, nowMillis);
        return false;
    }

    /** 인증이 끝나면(성공) 기록을 지운다 */
    public void forget(long verificationId) {
        lastAsked.remove(verificationId);
    }

    int sizeForTest() {
        return lastAsked.size();
    }
}