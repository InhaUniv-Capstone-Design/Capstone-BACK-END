package com.gamcho.yakyeon.domain.medication.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.DrugNameMatcher.Candidate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DrugNameMatcherTest {

    private static Candidate c(long id, String name) {
        return new Candidate(id, name);
    }

    private static String match(String ocr, Candidate... candidates) {
        return String.valueOf(DrugNameMatcher.matchId(ocr, List.of(candidates)));
    }

    @Test
    @DisplayName("처방전의 상품명+함량과 DB의 상품명(성분명 괄호)이 같은 제품으로 맞춰진다")
    void matchesBrandNameWithParentheses() {
        assertThat(match("메토폴정 500mg", c(302, "메토폴정(메트포르민염산염)"))).isEqualTo("302");
        assertThat(match("뉴로스틴정 10mg", c(4585, "뉴로스틴정(로수바스타틴칼슘)"))).isEqualTo("4585");
        assertThat(match("트리람정 0.25mg", c(11, "트리람정0.25밀리그램(트리아졸람)"))).as("함량 표기가 달라도(mg와 밀리그램)").isEqualTo("11");
        assertThat(match("더마졸정", c(12, "더마졸정(케토코나졸)(수출용)"))).isEqualTo("12");
    }

    @Test
    @DisplayName("옛 방식(글자가 완전히 같음)도 그대로 맞춰진다")
    void exactNamesStillMatch() {
        assertThat(match("메트포르민정500mg", c(1, "메트포르민정500mg"))).isEqualTo("1");
        assertThat(match("타이레놀", c(2, "타이레놀"))).isEqualTo("2");
    }

    @Test
    @DisplayName("공백, 대소문자, 기호가 달라도 같은 상품명이다")
    void ignoresSpacesCaseAndSymbols() {
        assertThat(match("타이레놀 정 500 mg", c(3, "타이레놀정500밀리그램"))).isEqualTo("3");
        assertThat(match("Tylenol ER 650mg", c(4, "TYLENOL-ER 650mg"))).isEqualTo("4");
    }

    @Test
    @DisplayName("함량만 다른 제품이 여럿이면 처방전 함량과 같은 것 하나로 정한다")
    void strengthSelectsAmongSeveral() {
        assertThat(match("메토폴정 500mg", c(1, "메토폴정500밀리그램(메트포르민염산염)"), c(2, "메토폴정850밀리그램(메트포르민염산염)"))).isEqualTo("1");
        assertThat(match("메토폴정 850mg", c(1, "메토폴정500밀리그램(메트포르민염산염)"), c(2, "메토폴정850밀리그램(메트포르민염산염)"))).isEqualTo("2");
        assertThat(match("아모잘탄정 5/50mg", c(1, "아모잘탄정5/50밀리그램"), c(2, "아모잘탄정5/100밀리그램"))).as("복합제 함량").isEqualTo("1");
        assertThat(match("트리람정 0.250mg", c(1, "트리람정0.25밀리그램"))).as("소수 끝자리 0은 같은 값").isEqualTo("1");
    }

    @Test
    @DisplayName("함량이 처방전에 없는데 DB에 함량이 다른 제품이 여럿이면 정하지 않는다")
    void ambiguousWithoutStrength() {
        assertThat(match("메토폴정", c(1, "메토폴정500밀리그램"), c(2, "메토폴정850밀리그램"))).isEqualTo("null");
    }

    @Test
    @DisplayName("같은 이름·함량의 제품이 업체별로 둘 이상이면 정하지 않는다 (잘못 정하면 위험)")
    void sameNameFromTwoManufacturers() {
        assertThat(match("디포민정 500mg", c(1, "디포민정500밀리그램"), c(2, "디포민정500밀리그램"))).isEqualTo("null");
        assertThat(match("디포민정", c(1, "디포민정(메트포르민염산염)"), c(2, "디포민정(메트포르민염산염)"))).isEqualTo("null");
    }

    @Test
    @DisplayName("함량이 다르면 맞다고 하지 않는다 (DB에 함량이 적혀 있을 때)")
    void differentStrengthDoesNotMatch() {
        assertThat(match("메토폴정 1000mg", c(1, "메토폴정500밀리그램"))).isEqualTo("null");
        assertThat(match("아모잘탄정 5/50mg", c(1, "아모잘탄정5mg/50mg"))).as("표기가 달라 확신할 수 없으면 정하지 않음").isEqualTo("null");
    }

    @Test
    @DisplayName("DB 제품명에 함량이 없으면 함량은 비교하지 않는다 (그 제품이 하나뿐일 때)")
    void candidateWithoutStrength() {
        assertThat(match("디포민정 500mg", c(1, "디포민정(메트포르민염산염)"))).isEqualTo("1");
    }

    @Test
    @DisplayName("상품명이 다르면 맞다고 하지 않는다 (비슷한 이름, 성분명만 같은 제품)")
    void differentProductsDoNotMatch() {
        assertThat(match("메토폴정 500mg", c(1, "메토폴엑스알정(메트포르민염산염)"))).isEqualTo("null");
        assertThat(match("메트포르민정", c(1, "메토폴정(메트포르민염산염)"))).as("성분명이 괄호 안에만 있으면 상품명이 아님").isEqualTo("null");
        assertThat(match("아토르바스타틴정 10mg", c(1, "아토르바스타틴칼슘정10mg"))).isEqualTo("null");
    }

    @Test
    @DisplayName("후보 중 맞는 것 하나만 골라낸다 (다른 제품이 섞여 있어도)")
    void picksTheOneAmongOthers() {
        assertThat(match("메토폴정 500mg", c(931, "메포정(염산메트포르민)"), c(1191, "디포민정(메트포르민염산염)"),
                c(302, "메토폴정(메트포르민염산염)"), c(2879, "아포민정(메트포르민염산염)"))).isEqualTo("302");
    }

    @Test
    @DisplayName("이름이 비었거나 너무 짧거나 후보가 없으면 정하지 않는다")
    void degenerateInputs() {
        assertThat(match("", c(1, "타이레놀"))).isEqualTo("null");
        assertThat(match("정", c(1, "정"))).isEqualTo("null");
        assertThat(match("500mg", c(1, "500mg"))).as("함량만 있는 이름").isEqualTo("null");
        assertThat(match("타이레놀")).isEqualTo("null");
        assertThat(String.valueOf(DrugNameMatcher.matchId(null, List.of(c(1, "타이레놀"))))).isEqualTo("null");
        assertThat(String.valueOf(DrugNameMatcher.matchId("타이레놀", null))).isEqualTo("null");
        assertThat(match("타이레놀", new Candidate(null, "타이레놀"), new Candidate(5L, null))).isEqualTo("null");
    }

    @Test
    @DisplayName("비교용 상품명과 함량 뽑기")
    void baseNameAndStrengths() {
        assertThat(DrugNameMatcher.baseName("메토폴정 500mg (Metformin)")).isEqualTo("메토폴정");
        assertThat(DrugNameMatcher.baseName("트리람정0.25밀리그램(트리아졸람)")).isEqualTo("트리람정");
        assertThat(DrugNameMatcher.baseName("아모잘탄정 5/50mg")).isEqualTo("아모잘탄정");
        assertThat(DrugNameMatcher.strengths("아모잘탄정 5/50mg").toString()).isEqualTo("[5/50]");
        assertThat(DrugNameMatcher.strengths("트리람정0.25밀리그램(트리아졸람 1mg)").toString()).as("괄호 안 숫자는 세지 않음").isEqualTo("[0.25]");
        assertThat(DrugNameMatcher.strengths("타이레놀").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("DB 검색어: 그대로, 첫 숫자 앞까지, 괄호를 뺀 뒤 첫 숫자 앞까지 (중복과 긴 글자 처리)")
    void searchTerms() {
        assertThat(DrugNameMatcher.searchTerms("메토폴정 500mg", 50).toString()).isEqualTo("[메토폴정 500mg, 메토폴정]");
        assertThat(DrugNameMatcher.searchTerms("메토폴정(메트포르민) 500mg", 50).toString()).isEqualTo("[메토폴정(메트포르민) 500mg, 메토폴정(메트포르민), 메토폴정]");
        assertThat(DrugNameMatcher.searchTerms("타이레놀", 50).toString()).isEqualTo("[타이레놀]");
        assertThat(DrugNameMatcher.searchTerms("가".repeat(80), 50).get(0).length()).isEqualTo(50);
        assertThat(DrugNameMatcher.searchTerms("  ", 50).isEmpty()).isTrue();
        assertThat(DrugNameMatcher.searchTerms(null, 50).isEmpty()).isTrue();
        assertThat(DrugNameMatcher.searchTerms("500mg", 50).toString()).as("숫자만 있으면 앞부분이 비어 첫 검색어만").isEqualTo("[500mg]");
    }
}