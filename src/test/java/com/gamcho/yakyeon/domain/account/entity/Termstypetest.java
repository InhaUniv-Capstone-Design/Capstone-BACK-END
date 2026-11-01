package com.gamcho.yakyeon.domain.account.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 코드의 약관 종류(TermsType)가 DB가 허용하는 값과 어긋나면, DB에는 있는 약관이 조회 목록에서 조용히 빠진다
 * (MEDICATION이 이렇게 빠졌었다). 그걸 테스트로 막는다. DB 제약을 바꾸면 이 목록도 같이 고친다.
 */
class TermsTypeTest {

    /** terms_version.terms_type의 CHECK 제약이 허용하는 값 (스키마 26-10-29 기준) */
    private static final Set<String> DB_VALUES = Set.of(
            "TOS", "PRIVACY", "PRIVACY_POLICY", "SENSITIVE_HEALTH", "SERVICE_NOTICE",
            "DELEGATION", "LEGAL_REP", "DEVICE", "MEDICATION");

    @Test
    @DisplayName("코드의 약관 종류가 DB가 허용하는 값과 같다")
    void enumMatchesDbCheck() {
        Set<String> code = Arrays.stream(TermsVersion.TermsType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(code.equals(DB_VALUES)).as("코드 " + code + " / DB " + DB_VALUES).isTrue();
    }
}