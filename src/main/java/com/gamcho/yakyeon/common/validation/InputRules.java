package com.gamcho.yakyeon.common.validation;

import java.util.regex.Pattern;

/**
 * 가입·비밀번호 변경의 입력 규칙. 앱·웹 가입 화면의 안내 문구와 같은 규칙이고, 서버가 최종 판단한다.
 * 화면만 믿지 않고 서버에서도 검사해야 하므로 DTO의 @Pattern과 서비스 코드가 같은 상수를 쓴다.
 */
public final class InputRules {

    /**
     * 6~20자의 영문, 숫자.
     * '#'로 시작하는 아이디는 탈퇴 계정용으로 예약돼 있는데(DB CHECK), 이 규칙이 '#'을 허용하지 않으므로 자동으로 걸러진다.
     */
    public static final String LOGIN_ID_REGEX = "^[A-Za-z0-9]{6,20}$";
    public static final String LOGIN_ID_MESSAGE = "아이디는 6~20자의 영문, 숫자만 사용할 수 있습니다.";

    /**
     * 영문, 숫자, 특수문자를 각각 1자 이상 포함한 8~64자.
     * 공백과 한글 같은 ASCII 밖의 문자는 허용하지 않는다. BCrypt가 앞 72바이트만 쓰는데,
     * 한글처럼 한 글자가 여러 바이트인 문자가 섞이면 64자라도 72바이트를 넘을 수 있기 때문이다.
     */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]{8,64}$";
    public static final String PASSWORD_MESSAGE =
            "비밀번호는 영문, 숫자, 특수문자를 모두 포함한 8~64자여야 합니다. (공백, 한글은 사용할 수 없습니다)";

    private static final Pattern LOGIN_ID = Pattern.compile(LOGIN_ID_REGEX);
    private static final Pattern PASSWORD = Pattern.compile(PASSWORD_REGEX);

    private InputRules() {
    }

    public static boolean isValidLoginId(String value) {
        return value != null && LOGIN_ID.matcher(value).matches();
    }

    public static boolean isValidPassword(String value) {
        return value != null && PASSWORD.matcher(value).matches();
    }
}