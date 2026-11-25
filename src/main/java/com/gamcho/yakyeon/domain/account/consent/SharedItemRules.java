package com.gamcho.yakyeon.domain.account.consent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 복약자 웹 동의(열람 범위)의 규칙 (스프링·DB 없는 순수 로직).
 *
 * 열람 범위는 6개 항목이다. 필수 3개(이름·연락처, 약물 정보, 사진)에 모두 동의해야 대리인 연동이 만들어지고,
 * 하나라도 비동의하면 연동은 만들어지지 않고 "거절됨(REJECTED)"으로만 기록된다.
 * 나머지 3개(복약 기록, 약통 이벤트, 병용금기 정보)는 동의하지 않으면 그 기능만 막힌다.
 */
public final class SharedItemRules {

    public static final String PERSONAL_INFO = "PERSONAL_INFO";
    public static final String MED_INFO = "MED_INFO";
    public static final String PHOTO = "PHOTO";
    public static final String DOSE_RECORD = "DOSE_RECORD";
    public static final String DEVICE_EVENT = "DEVICE_EVENT";
    public static final String DUR_INFO = "DUR_INFO";

    /** 6개 항목. 응답과 저장은 항상 이 순서다 */
    public static final List<String> ALL =
            List.of(PERSONAL_INFO, MED_INFO, PHOTO, DOSE_RECORD, DEVICE_EVENT, DUR_INFO);

    /** 비동의하면 연동을 쓸 수 없는 필수 항목 */
    public static final List<String> REQUIRED = List.of(PERSONAL_INFO, MED_INFO, PHOTO);

    /** "대리인과의 관계" 최대 길이. 값 목록은 검사하지 않고 그대로 저장한다 */
    public static final int RELATIONSHIP_MAX = 20;

    private SharedItemRules() {
    }

    /** 6개 항목이 모두 true 또는 false로 있고 모르는 항목이 없는지. 올바르면 true */
    public static boolean isComplete(Map<String, Boolean> consents) {
        if (consents == null || consents.size() != ALL.size()) {
            return false;
        }
        for (String item : ALL) {
            if (!consents.containsKey(item) || consents.get(item) == null) {
                return false;
            }
        }
        return true;   // 크기가 6이고 6개가 모두 있으면 모르는 항목은 있을 수 없다
    }

    /** 동의한(true) 항목을 6개 항목의 정해진 순서로 돌려준다. isComplete가 true인 입력에만 쓴다 */
    public static List<String> grantedItems(Map<String, Boolean> consents) {
        List<String> granted = new ArrayList<>();
        for (String item : ALL) {
            if (Boolean.TRUE.equals(consents.get(item))) {
                granted.add(item);
            }
        }
        return granted;
    }

    /** 필수 3개에 모두 동의했는지 */
    public static boolean allRequiredGranted(Collection<String> granted) {
        return granted.containsAll(REQUIRED);
    }

    /** "대리인과의 관계": 앞뒤 공백을 지우고, 비어 있으면 null. 20자를 넘는지는 요청 검증이 거른다 */
    public static String normalizeRelationship(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * DB 배열(VARCHAR(20)[])에 넣을 문자열 "{A,B}"를 만든다. 정해진 6개 항목 이름만 들어갈 수 있어서
     * (그 밖의 값이면 거절) 문자열을 이어 붙여도 안전하다. 순서는 항상 ALL 순서다.
     */
    public static String toArrayLiteral(Collection<String> items) {
        for (String item : items) {
            if (!ALL.contains(item)) {
                throw new IllegalArgumentException("알 수 없는 열람 항목입니다: " + item);
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String item : ALL) {
            if (items.contains(item)) {
                ordered.add(item);
            }
        }
        return "{" + String.join(",", ordered) + "}";
    }

    /** DB에서 array_to_string(shared_items, ',')로 읽은 문자열을 항목 목록으로 바꾼다 (ALL 순서, 모르는 값은 버린다) */
    public static List<String> parse(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        List<String> found = List.of(commaSeparated.split(","));
        List<String> ordered = new ArrayList<>();
        for (String item : ALL) {
            if (found.contains(item)) {
                ordered.add(item);
            }
        }
        return ordered;
    }

    /** 두 항목 목록이 같은 집합인지 (순서 무관) */
    public static boolean sameItems(Collection<String> a, Collection<String> b) {
        return a.size() == b.size() && a.containsAll(b);
    }
}