package com.gamcho.yakyeon.common.util;

import java.util.regex.Pattern;

/**
 * 휴대폰 번호 정규화/형식 검증 공통 유틸.
 * 해시·암호화 전에 항상 같은 형식(숫자만)으로 맞춰야 같은 번호가 같은 해시가 된다.
 */
public final class PhoneNumbers {

    /** 010으로 시작하는 11자리. 문자 인증 업체(OCTOMO)가 이 형식만 받아서 맞췄다 (InputRules.PHONE_REGEX와 같다) */
    private static final Pattern MOBILE = Pattern.compile("^010[0-9]{8}$");

    private PhoneNumbers() {
    }

    /** 하이픈·공백 등 숫자가 아닌 문자를 모두 제거 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /** 정규화된(숫자만 있는) 번호가 국내 휴대폰 번호 형식인지 */
    public static boolean isValidMobile(String normalized) {
        return normalized != null && MOBILE.matcher(normalized).matches();
    }
}