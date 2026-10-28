package com.gamcho.yakyeon.domain.account.logic;

/** 화면에 보여줄 휴대폰 번호를 가리는 규칙 (스프링 없는 순수 로직). 앞 3자리와 뒤 4자리만 보인다. */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    /** "01012345678" → "010-****-5678", "0111234567" → "011-***-4567". 형식을 알 수 없으면 "****" */
    public static String mask(String phone) {
        if (phone == null) {
            return "****";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 11) {
            return "****";
        }
        return digits.substring(0, 3) + "-" + "*".repeat(digits.length() - 7) + "-" + digits.substring(digits.length() - 4);
    }
}