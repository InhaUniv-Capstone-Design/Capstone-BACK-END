package com.gamcho.yakyeon.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FieldNamesTest {

    @Test
    @DisplayName("자바 필드 이름을 snake_case로 바꾼다")
    void camelToSnake() {
        assertThat(FieldNames.toSnakeCase("fcmToken")).isEqualTo("fcm_token");
        assertThat(FieldNames.toSnakeCase("loginId")).isEqualTo("login_id");
        assertThat(FieldNames.toSnakeCase("birthDate")).isEqualTo("birth_date");
        assertThat(FieldNames.toSnakeCase("newPasswordConfirm")).isEqualTo("new_password_confirm");
    }

    @Test
    @DisplayName("이미 소문자인 이름과 목록·중첩 경로는 구조를 유지한다")
    void keepsStructure() {
        assertThat(FieldNames.toSnakeCase("name")).isEqualTo("name");
        assertThat(FieldNames.toSnakeCase("settings[0].settingKey")).isEqualTo("settings[0].setting_key");
        assertThat(FieldNames.toSnakeCase("agreements[2].termsVersionId")).isEqualTo("agreements[2].terms_version_id");
        assertThat(FieldNames.toSnakeCase("address2Line")).isEqualTo("address2_line");
    }

    @Test
    @DisplayName("null이나 빈 값은 빈 문자열로 돌려준다")
    void nullAndEmpty() {
        assertThat(FieldNames.toSnakeCase(null)).isEqualTo("");
        assertThat(FieldNames.toSnakeCase("")).isEqualTo("");
    }
}