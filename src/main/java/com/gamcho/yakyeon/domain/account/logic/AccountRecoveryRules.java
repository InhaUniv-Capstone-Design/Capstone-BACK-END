package com.gamcho.yakyeon.domain.account.logic;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 아이디·비밀번호 찾기에서 쓰는 규칙 (스프링·DB 없는 순수 로직).
 *
 * 이름 비교: 입력할 때 생기는 사소한 차이는 같은 이름으로 본다 - 공백(가운데 공백, 앞뒤 공백, 전각·줄바꿈 없는 공백),
 * 한글 조합 방식(자모 분리형과 완성형), 영문 대소문자. 그 밖의 차이(글자가 다름)는 다른 이름이다.
 */
public final class AccountRecoveryRules {

    private AccountRecoveryRules() {
    }

    /** 비교용 이름: 한글을 완성형으로 합치고, 모든 공백과 보이지 않는 글자를 없애고, 소문자로 만든다 */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String composed = Normalizer.normalize(name, Normalizer.Form.NFC);
        return composed.replaceAll("[\\s\\u00A0\\u200B-\\u200D\\u2060\\u3000\\uFEFF]+", "").toLowerCase(Locale.ROOT);
    }

    /** 둘 다 비어 있지 않고 비교용 이름이 같을 때만 같은 이름 */
    public static boolean sameName(String stored, String entered) {
        String a = normalizeName(stored);
        String b = normalizeName(entered);
        return !a.isEmpty() && !b.isEmpty() && a.equals(b);
    }
}