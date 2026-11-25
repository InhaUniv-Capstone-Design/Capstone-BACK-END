package com.gamcho.yakyeon.common.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * "정해진 시간 동안 최대 N번" 세기 (스프링 없음). 키마다 첫 호출 때 창이 열리고, 창이 끝나면 처음부터 센다.
 * 서버 한 대의 메모리에만 있어서 서버를 다시 켜면 초기화된다(여러 대로 늘리면 대수만큼 한도가 늘어난다).
 *
 * 키가 끝없이 늘어나는 공격(주소를 계속 바꾸는 요청)에 메모리가 차지 않도록 키 수에 상한이 있다. 상한에 이르면
 * 먼저 끝난 창을 치우고, 그래도 가득 차 있으면 새 키는 거절한다(서버를 지키는 쪽을 택한 것이다).
 */
public final class FixedWindowCounter {

    private record Window(long startSecond, long windowSeconds, int count) {
    }

    private static final int PRUNE_EVERY = 512;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final int maxKeys;

    public FixedWindowCounter(int maxKeys) {
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys는 1 이상이어야 합니다.");
        }
        this.maxKeys = maxKeys;
    }

    /**
     * 한도 안이면 횟수를 하나 올리고 true, 한도를 넘었으면 올리지 않고 false.
     *
     * @param nowEpochSecond 지금 시각(초). 테스트에서 시계를 바꿔 끼우려고 밖에서 받는다
     */
    public boolean tryAcquire(String key, int limit, long windowSeconds, long nowEpochSecond) {
        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("limit과 windowSeconds는 1 이상이어야 합니다.");
        }
        if (operations.incrementAndGet() % PRUNE_EVERY == 0 || windows.size() >= maxKeys) {
            prune(nowEpochSecond);
        }
        if (!windows.containsKey(key) && windows.size() >= maxKeys) {
            return false;   // 가득 참: 새 키는 받지 않는다
        }
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || nowEpochSecond - w.startSecond() >= w.windowSeconds()) {
                allowed[0] = true;
                return new Window(nowEpochSecond, windowSeconds, 1);
            }
            if (w.count() < limit) {
                allowed[0] = true;
                return new Window(w.startSecond(), w.windowSeconds(), w.count() + 1);
            }
            return w;
        });
        return allowed[0];
    }

    /** 이미 끝난 창을 치운다 */
    void prune(long nowEpochSecond) {
        windows.entrySet().removeIf(e -> nowEpochSecond - e.getValue().startSecond() >= e.getValue().windowSeconds());
    }

    int size() {
        return windows.size();
    }
}