package com.gamcho.yakyeon.domain.account.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountRecoveryRulesTest {

    @Test
    @DisplayName("같은 이름이면 맞다")
    void sameNameMatches() {
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍길동")).isTrue();
    }

    @Test
    @DisplayName("공백 차이는 같은 이름이다 (앞뒤, 가운데, 전각, 줄바꿈 없는 공백)")
    void whitespaceDifferencesAreIgnored() {
        assertThat(AccountRecoveryRules.sameName("홍길동", " 홍길동 ")).isTrue();
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍 길동")).isTrue();
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍\u3000길동")).isTrue();
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍\u00A0길동")).isTrue();
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍\u200B길동")).as("보이지 않는 글자").isTrue();
    }

    @Test
    @DisplayName("한글을 자모로 풀어 쓴 입력(맥 방식)도 완성형과 같은 이름이다")
    void decomposedHangulIsTheSameName() {
        String decomposed = java.text.Normalizer.normalize("홍길동", java.text.Normalizer.Form.NFD);
        assertThat(decomposed.equals("홍길동")).as("테스트 전제: 두 문자열은 바이트가 다르다").isFalse();
        assertThat(AccountRecoveryRules.sameName("홍길동", decomposed)).isTrue();
    }

    @Test
    @DisplayName("영문 이름은 대소문자를 구분하지 않는다")
    void latinCaseIsIgnored() {
        assertThat(AccountRecoveryRules.sameName("John Smith", "john smith")).isTrue();
        assertThat(AccountRecoveryRules.sameName("John Smith", "JOHNSMITH")).isTrue();
    }

    @Test
    @DisplayName("글자가 다르면 다른 이름이다 (한 글자 차이, 앞부분만, 일부 포함)")
    void differentNamesDoNotMatch() {
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍길순")).isFalse();
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍길")).isFalse();
        assertThat(AccountRecoveryRules.sameName("홍길동", "홍길동2")).isFalse();
        assertThat(AccountRecoveryRules.sameName("홍길동", "김홍길동")).isFalse();
    }

    @Test
    @DisplayName("비어 있거나 없는 이름은 어떤 것과도 같지 않다 (빈 이름끼리도)")
    void blankNamesNeverMatch() {
        assertThat(AccountRecoveryRules.sameName("", "")).isFalse();
        assertThat(AccountRecoveryRules.sameName(null, "홍길동")).isFalse();
        assertThat(AccountRecoveryRules.sameName("홍길동", null)).isFalse();
        assertThat(AccountRecoveryRules.sameName(" ", "\u3000")).isFalse();
        assertThat(AccountRecoveryRules.sameName(null, null)).isFalse();
    }
}