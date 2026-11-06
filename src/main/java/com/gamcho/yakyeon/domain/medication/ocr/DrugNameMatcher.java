package com.gamcho.yakyeon.domain.medication.ocr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 처방전에서 읽은 약 이름을 약물 DB의 제품과 맞춘다 (스프링·DB 없는 순수 로직).
 *
 * 처방전에는 "메토폴정 500mg"처럼 상품명과 함량이 적히지만, DB의 제품명은 "메토폴정(메트포르민염산염)",
 * "트리람정0.25밀리그램(트리아졸람)"처럼 괄호 안에 성분명이 붙고 함량 표기도 다르다. 글자가 완전히 같아야만 맞다고 하면
 * 사실상 자동으로 정해지는 약이 없어서, 아래 규칙으로 비교한다.
 *
 *  1) 양쪽에서 괄호 안 글자와 함량(숫자 + 단위)을 빼고, 공백·기호를 없애고, 소문자로 만든 "상품명"이 같은 제품만 후보로 본다.
 *  2) 처방전 이름에 함량(숫자)이 있으면, DB 제품명의 함량과 같은 제품만 남긴다.
 *     DB 제품명에 함량이 아예 없으면 비교하지 않는다(그 제품이 하나뿐이면 그대로 인정).
 *  3) 남은 제품이 "정확히 하나일 때만" 그 id를 돌려준다. 둘 이상이면(업체가 다르거나 함량만 다른 제품) 정하지 않는다.
 *     잘못 정하면 다른 약으로 병용금기 검사가 돌아가 위험하므로, 확실하지 않으면 사용자가 고르게 한다.
 */
public final class DrugNameMatcher {

    /** 후보 제품: DB의 id와 제품명 */
    public record Candidate(Long drugId, String productName) {
    }

    private static final Pattern PARENTHESES = Pattern.compile("[(\\[][^)\\]]*[)\\]]");
    private static final String UNIT = "(?:mg|밀리그램|㎎|mcg|μg|µg|마이크로그램|g|그램|ml|밀리리터|㎖|iu|단위|%)";
    /** 함량: 숫자(소수, 5/50 같은 복합제 표기 포함) + 선택적인 단위 */
    private static final Pattern STRENGTH = Pattern.compile("(\\d+(?:[./]\\d+)*)\\s*" + UNIT + "?", Pattern.CASE_INSENSITIVE);

    private DrugNameMatcher() {
    }

    /** 자동으로 정할 수 있으면 그 제품의 id, 아니면 null */
    public static Long matchId(String ocrName, List<Candidate> candidates) {
        String base = baseName(ocrName);
        if (base.length() < 2 || candidates == null) {
            return null;
        }
        Set<String> wanted = strengths(ocrName);
        List<Candidate> matched = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c == null || c.drugId() == null || c.productName() == null || !baseName(c.productName()).equals(base)) {
                continue;
            }
            Set<String> have = strengths(c.productName());
            if (wanted.isEmpty() || have.isEmpty() || have.containsAll(wanted)) {
                matched.add(c);
            }
        }
        return matched.size() == 1 ? matched.get(0).drugId() : null;
    }

    /** 괄호 안 글자와 함량을 뺀, 비교용 상품명 (공백·기호 제거, 소문자). 예: "메토폴정 500mg" → "메토폴정" */
    static String baseName(String name) {
        if (name == null) {
            return "";
        }
        String s = PARENTHESES.matcher(name).replaceAll(" ");
        s = STRENGTH.matcher(s).replaceAll(" ");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
    }

    /** 이름(괄호 밖)에 들어 있는 함량 숫자들. "0.25밀리그램"과 "0.25mg"는 같은 "0.25"다 */
    static Set<String> strengths(String name) {
        Set<String> result = new LinkedHashSet<>();
        if (name == null) {
            return result;
        }
        Matcher m = STRENGTH.matcher(PARENTHESES.matcher(name).replaceAll(" "));
        while (m.find()) {
            result.add(normalizeNumber(m.group(1)));
        }
        return result;
    }

    /** "500.0" → "500", "5/050" → "5/50" (실패하면 글자 그대로) */
    private static String normalizeNumber(String token) {
        List<String> parts = new ArrayList<>();
        for (String part : token.split("/")) {
            try {
                parts.add(new BigDecimal(part).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException e) {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }

    /**
     * 약물 DB에서 찾아 볼 검색어들 (앞의 것부터 시도해서 결과가 나오면 멈춘다).
     * 1) 읽은 이름 그대로  2) 첫 숫자(함량) 앞까지  3) 괄호를 뺀 뒤 첫 숫자 앞까지. 중복과 빈 값은 뺀다.
     */
    public static List<String> searchTerms(String rawName, int maxLength) {
        Set<String> terms = new LinkedHashSet<>();
        if (rawName == null) {
            return List.of();
        }
        String full = rawName.trim().replaceAll("\\s+", " ");
        add(terms, full, maxLength);
        add(terms, full.replaceAll("[0-9].*$", ""), maxLength);
        add(terms, PARENTHESES.matcher(full).replaceAll(" ").replaceAll("[0-9].*$", ""), maxLength);
        return List.copyOf(terms);
    }

    private static void add(Set<String> terms, String term, int maxLength) {
        String t = term.trim().replaceAll("\\s+", " ");
        if (t.length() > maxLength) {
            t = t.substring(0, maxLength).trim();
        }
        if (t.length() >= 1) {
            terms.add(t);
        }
    }
}