package com.gamcho.yakyeon.common.exception;

/**
 * 검증 오류 메시지에 붙는 필드 이름을 JSON 이름(snake_case)으로 바꾼다.
 * 응답 JSON은 snake_case인데 오류 메시지만 자바 이름(fcmToken)이면 프론트가 어느 칸의 오류인지 알아보기 어렵다.
 * 예: fcmToken → fcm_token, settings[0].settingKey → settings[0].setting_key
 */
public final class FieldNames {

    private FieldNames() {
    }

    public static String toSnakeCase(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                // 소문자·숫자 바로 뒤의 대문자 앞에서만 끊는다 (이미 '_'나 '.' 뒤라면 그대로)
                if (i > 0 && isLowerOrDigit(name.charAt(i - 1))) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isLowerOrDigit(char c) {
        return Character.isLowerCase(c) || Character.isDigit(c);
    }
}