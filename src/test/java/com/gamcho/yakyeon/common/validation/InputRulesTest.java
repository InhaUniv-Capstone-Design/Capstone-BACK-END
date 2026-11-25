package com.gamcho.yakyeon.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InputRulesTest {

    @Test
    @DisplayName("아이디: 6~20자의 영문·숫자만 허용한다")
    void validLoginIds() {
        String[] valid = {
                "abcdef", "user01", "testpatient1", "TestGuardian1", "agent001",
                "a1b2c3d4e5f6g7h8i9j0" // 정확히 20자
        };
        for (String id : valid) {
            assertThat(InputRules.isValidLoginId(id)).as("유효해야 함: " + id).isTrue();
        }
    }

    @Test
    @DisplayName("아이디: 짧거나 길거나 영문·숫자 밖의 문자가 있으면 거절한다")
    void invalidLoginIds() {
        String[] invalid = {
                "abc12",                    // 5자
                "a1b2c3d4e5f6g7h8i9j0k",    // 21자
                "#abcdef",                  // '#'로 시작(탈퇴 계정용 예약)도 걸러진다
                "user_01", "user-01", "user 01", "user.01",
                "한글아이디입니다", "abc한글def",
                " abcdef", "abcdef ", "",
                "abcdef\n"                  // 끝 줄바꿈으로 규칙을 우회하지 못한다
        };
        for (String id : invalid) {
            assertThat(InputRules.isValidLoginId(id)).as("거절해야 함: [" + id + "]").isFalse();
        }
        assertThat(InputRules.isValidLoginId(null)).isFalse();
    }

    @Test
    @DisplayName("비밀번호: 영문·숫자·특수문자를 모두 포함한 8~64자를 허용한다")
    void validPasswords() {
        String[] valid = {
                "Test1234!pw", "NewPass5678!", "a1!aaaaa",   // 정확히 8자
                "Abcd1234~", "pw@12345", "abc1!x1!x1!x1!"
        };
        for (String pw : valid) {
            assertThat(InputRules.isValidPassword(pw)).as("유효해야 함: " + pw).isTrue();
        }
        String max = "Aa1!" + "x".repeat(60); // 정확히 64자
        assertThat(max.length()).isEqualTo(64);
        assertThat(InputRules.isValidPassword(max)).isTrue();
    }

    @Test
    @DisplayName("비밀번호: 구성이 모자라거나 길이·문자 범위를 벗어나면 거절한다")
    void invalidPasswords() {
        String[] invalid = {
                "Sh0rt!1",          // 7자
                "alllowercase!",    // 숫자 없음
                "12345678!",        // 영문 없음
                "Abcdefg1",         // 특수문자 없음
                "Abcd 1234!",       // 공백
                "비밀번호Abc123!",    // 한글 (BCrypt 72바이트 한도 때문에 ASCII만)
                "",
                "Pass1234!\n"       // 끝 줄바꿈
        };
        for (String pw : invalid) {
            assertThat(InputRules.isValidPassword(pw)).as("거절해야 함: [" + pw + "]").isFalse();
        }
        String tooLong = "Aa1!" + "x".repeat(61); // 65자
        assertThat(InputRules.isValidPassword(tooLong)).isFalse();
        assertThat(InputRules.isValidPassword(null)).isFalse();
    }
}