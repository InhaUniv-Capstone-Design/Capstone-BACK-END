package com.gamcho.yakyeon.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.common.validation.InputRules;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 문자 인증 업체(OCTOMO)가 받는 형식인 010으로 시작하는 11자리만 휴대폰 번호로 본다. */
class PhoneNumbersTest {

    private static final String[] VALID = {"01012345678", "01000000000", "01099999999"};
    private static final String[] INVALID = {
            "01112345678", "0161234567", "01612345678", "0101234567", "010123456789", "02012345678",
            "1012345678", "", "010-1234-5678", "010 1234 5678", "0101234567a", "+821012345678"};

    @Test
    @DisplayName("010으로 시작하는 11자리 숫자만 휴대폰 번호다")
    void onlyZeroOneZeroElevenDigits() {
        for (String phone : VALID) {
            assertThat(PhoneNumbers.isValidMobile(phone)).as(phone).isTrue();
        }
        for (String phone : INVALID) {
            assertThat(PhoneNumbers.isValidMobile(phone)).as("'" + phone + "'").isFalse();
        }
        assertThat(PhoneNumbers.isValidMobile(null)).isFalse();
    }

    @Test
    @DisplayName("하이픈과 공백은 normalize로 먼저 걷어낸다")
    void normalizeStripsNonDigits() {
        assertThat(PhoneNumbers.normalize("010-1234-5678")).isEqualTo("01012345678");
        assertThat(PhoneNumbers.normalize(" 010 1234 5678 ")).isEqualTo("01012345678");
        assertThat(PhoneNumbers.isValidMobile(PhoneNumbers.normalize("010-1234-5678"))).isTrue();
        assertThat(PhoneNumbers.normalize(null)).isEqualTo("");
    }

    @Test
    @DisplayName("요청 검증용 정규식(InputRules.PHONE_REGEX)과 서비스의 번호 검사가 같은 결과를 낸다")
    void requestRuleAndServiceRuleAgree() {
        Pattern pattern = Pattern.compile(InputRules.PHONE_REGEX);
        for (String phone : VALID) {
            assertThat(pattern.matcher(phone).matches()).as(phone).isTrue();
        }
        for (String phone : INVALID) {
            assertThat(pattern.matcher(phone).matches()).as("'" + phone + "'").isFalse();
        }
    }
}