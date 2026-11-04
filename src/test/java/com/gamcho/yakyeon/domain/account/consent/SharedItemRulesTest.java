package com.gamcho.yakyeon.domain.account.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharedItemRulesTest {

    private static Map<String, Boolean> consents(boolean personal, boolean med, boolean photo,
                                                 boolean dose, boolean device, boolean dur) {
        Map<String, Boolean> map = new HashMap<>();
        map.put("PERSONAL_INFO", personal);
        map.put("MED_INFO", med);
        map.put("PHOTO", photo);
        map.put("DOSE_RECORD", dose);
        map.put("DEVICE_EVENT", device);
        map.put("DUR_INFO", dur);
        return map;
    }

    @Test
    @DisplayName("열람 항목은 6개이고 필수는 이름·연락처, 약물 정보, 사진 3개다")
    void itemsAndRequired() {
        assertThat(SharedItemRules.ALL.size()).isEqualTo(6);
        assertThat(SharedItemRules.REQUIRED.size()).isEqualTo(3);
        assertThat(SharedItemRules.ALL.get(0)).isEqualTo("PERSONAL_INFO");
        assertThat(SharedItemRules.REQUIRED.containsAll(List.of("PERSONAL_INFO", "MED_INFO", "PHOTO"))).isTrue();
        assertThat(SharedItemRules.REQUIRED.contains("DOSE_RECORD")).isFalse();
    }

    @Test
    @DisplayName("6개 항목이 모두 true 또는 false로 있어야 올바른 요청이다")
    void completeConsents() {
        assertThat(SharedItemRules.isComplete(consents(true, true, true, true, true, true))).isTrue();
        assertThat(SharedItemRules.isComplete(consents(false, false, false, false, false, false))).isTrue();
    }

    @Test
    @DisplayName("항목이 빠지거나 모르는 항목이 있거나 값이 비어 있으면 올바르지 않다")
    void incompleteConsents() {
        Map<String, Boolean> missing = consents(true, true, true, true, true, true);
        missing.remove("DUR_INFO");
        assertThat(SharedItemRules.isComplete(missing)).as("한 항목 빠짐").isFalse();

        Map<String, Boolean> extra = consents(true, true, true, true, true, true);
        extra.put("EMAIL", true);
        assertThat(SharedItemRules.isComplete(extra)).as("모르는 항목 추가").isFalse();

        Map<String, Boolean> misspelled = consents(true, true, true, true, true, true);
        misspelled.remove("DEVICE_EVENT");
        misspelled.put("DEVICE_EVENTS", true);
        assertThat(SharedItemRules.isComplete(misspelled)).as("이름이 틀림(개수는 6개)").isFalse();

        Map<String, Boolean> nullValue = consents(true, true, true, true, true, true);
        nullValue.put("PHOTO", null);
        assertThat(SharedItemRules.isComplete(nullValue)).as("값이 null").isFalse();

        assertThat(SharedItemRules.isComplete(new HashMap<>())).as("빈 객체").isFalse();
        assertThat(SharedItemRules.isComplete(null)).as("없음").isFalse();
    }

    @Test
    @DisplayName("동의한 항목만 정해진 순서로 돌려준다")
    void grantedItemsInOrder() {
        List<String> granted = SharedItemRules.grantedItems(consents(true, true, true, false, true, true));
        assertThat(granted.size()).isEqualTo(5);
        assertThat(granted.get(0)).isEqualTo("PERSONAL_INFO");
        assertThat(granted.get(3)).isEqualTo("DEVICE_EVENT");
        assertThat(granted.contains("DOSE_RECORD")).isFalse();
        assertThat(SharedItemRules.grantedItems(consents(false, false, false, false, false, false)).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("필수 3개 모두에 동의해야 연동이 만들어진다 (선택 3개는 영향 없음)")
    void requiredItems() {
        assertThat(SharedItemRules.allRequiredGranted(SharedItemRules.grantedItems(consents(true, true, true, false, false, false)))).isTrue();
        assertThat(SharedItemRules.allRequiredGranted(SharedItemRules.grantedItems(consents(true, true, true, true, true, true)))).isTrue();
        assertThat(SharedItemRules.allRequiredGranted(SharedItemRules.grantedItems(consents(false, true, true, true, true, true)))).as("이름·연락처 비동의").isFalse();
        assertThat(SharedItemRules.allRequiredGranted(SharedItemRules.grantedItems(consents(true, false, true, true, true, true)))).as("약물 정보 비동의").isFalse();
        assertThat(SharedItemRules.allRequiredGranted(SharedItemRules.grantedItems(consents(true, true, false, true, true, true)))).as("사진 비동의").isFalse();
        assertThat(SharedItemRules.allRequiredGranted(SharedItemRules.grantedItems(consents(false, false, false, true, true, true)))).as("선택 3개만").isFalse();
    }

    @Test
    @DisplayName("대리인과의 관계: 앞뒤 공백을 지우고 비어 있으면 없는 값으로 본다")
    void relationship() {
        assertThat(SharedItemRules.normalizeRelationship("  자녀 ")).isEqualTo("자녀");
        assertThat(SharedItemRules.normalizeRelationship("")).isEqualTo(null);
        assertThat(SharedItemRules.normalizeRelationship("   ")).isEqualTo(null);
        assertThat(SharedItemRules.normalizeRelationship(null)).isEqualTo(null);
    }

    @Test
    @DisplayName("DB 배열 문자열은 항상 정해진 순서로 만들고, 비어 있으면 {}")
    void arrayLiteral() {
        assertThat(SharedItemRules.toArrayLiteral(List.of("DUR_INFO", "PERSONAL_INFO", "MED_INFO")))
                .isEqualTo("{PERSONAL_INFO,MED_INFO,DUR_INFO}");
        assertThat(SharedItemRules.toArrayLiteral(List.of())).isEqualTo("{}");
        assertThat(SharedItemRules.toArrayLiteral(SharedItemRules.ALL))
                .isEqualTo("{PERSONAL_INFO,MED_INFO,PHOTO,DOSE_RECORD,DEVICE_EVENT,DUR_INFO}");
    }

    @Test
    @DisplayName("정해진 6개 이름이 아닌 값은 DB 문자열에 넣지 않는다 (문자열 이어 붙이기가 안전하도록)")
    void arrayLiteralRejectsAnythingElse() {
        for (String bad : new String[]{"EMAIL", "med_info", "MED_INFO}'; DROP TABLE x;--", "", "PERSONAL_INFO,MED_INFO"}) {
            boolean rejected = false;
            try {
                SharedItemRules.toArrayLiteral(List.of(bad));
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            assertThat(rejected).as("거절돼야 함: " + bad).isTrue();
        }
    }

    @Test
    @DisplayName("DB에서 읽은 쉼표 문자열을 정해진 순서의 목록으로 바꾼다 (비어 있거나 모르는 값은 버린다)")
    void parse() {
        assertThat(SharedItemRules.parse("MED_INFO,PERSONAL_INFO").get(0)).isEqualTo("PERSONAL_INFO");
        assertThat(SharedItemRules.parse("DUR_INFO,PERSONAL_INFO,MED_INFO,PHOTO").size()).isEqualTo(4);
        assertThat(SharedItemRules.parse("").isEmpty()).isTrue();
        assertThat(SharedItemRules.parse(null).isEmpty()).isTrue();
        assertThat(SharedItemRules.parse("   ").isEmpty()).isTrue();
        assertThat(SharedItemRules.parse("PERSONAL_INFO,UNKNOWN").size()).as("모르는 값 버림").isEqualTo(1);
    }

    @Test
    @DisplayName("같은 항목 집합인지 순서와 상관없이 비교한다")
    void sameItems() {
        assertThat(SharedItemRules.sameItems(List.of("MED_INFO", "PERSONAL_INFO"), List.of("PERSONAL_INFO", "MED_INFO"))).isTrue();
        assertThat(SharedItemRules.sameItems(List.of("MED_INFO"), List.of("MED_INFO", "PHOTO"))).isFalse();
        assertThat(SharedItemRules.sameItems(List.of(), List.of())).isTrue();
        assertThat(SharedItemRules.sameItems(List.of("MED_INFO"), List.of("PHOTO"))).isFalse();
    }
}