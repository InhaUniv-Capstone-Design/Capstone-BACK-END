package com.gamcho.yakyeon.domain.notification.logic;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 알림 목록 조회와 알림 설정의 규칙 (스프링·DB 없는 순수 로직) */
public final class NotificationRules {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    /** 페이지 번호 상한. 크기 100과 곱해도 정수 범위(건너뛸 줄 수)를 넘지 않게 하는 안전장치다 */
    public static final int MAX_PAGE = 100_000;

    private NotificationRules() {
    }

    /** 페이지 번호는 0부터이고 한 번에 1~100개까지 읽을 수 있다 */
    public static boolean isValidPaging(int page, int size) {
        return page >= 0 && page <= MAX_PAGE && size >= 1 && size <= MAX_PAGE_SIZE;
    }

    /**
     * 저장된 설정에 없는 항목은 켜짐으로 채워서 8개를 항상 같은 순서로 돌려준다.
     * 설정은 사용자가 처음 바꿀 때까지 행이 없으므로, "행이 없다 = 켜짐(기본값)"이다.
     */
    public static Map<NotificationSettingKey, Boolean> withDefaults(Map<NotificationSettingKey, Boolean> stored) {
        Map<NotificationSettingKey, Boolean> result = new LinkedHashMap<>();
        for (NotificationSettingKey key : NotificationSettingKey.values()) {
            Boolean value = stored == null ? null : stored.get(key);
            result.put(key, value == null || value);
        }
        return result;
    }

    /** 같은 설정 키가 한 요청에 두 번 들어 있는지 (어느 쪽을 따를지 알 수 없어서 거절한다) */
    public static boolean hasDuplicateKey(List<NotificationSettingKey> keys) {
        Set<NotificationSettingKey> seen = new HashSet<>();
        for (NotificationSettingKey key : keys) {
            if (!seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    /** DB에서 읽은 문자열 키를 변환한다. 정의되지 않은 값은 건너뛴다 (DB 제약이 막지만 방어적으로) */
    public static Map<NotificationSettingKey, Boolean> parseStored(Map<String, Boolean> raw) {
        Map<NotificationSettingKey, Boolean> result = new EnumMap<>(NotificationSettingKey.class);
        raw.forEach((k, v) -> {
            try {
                result.put(NotificationSettingKey.valueOf(k), v);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 키는 무시
            }
        });
        return result;
    }
}