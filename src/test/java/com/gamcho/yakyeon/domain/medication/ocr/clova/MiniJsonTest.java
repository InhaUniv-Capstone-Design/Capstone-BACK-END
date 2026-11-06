package com.gamcho.yakyeon.domain.medication.ocr.clova;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MiniJsonTest {

    private static boolean rejects(String json) {
        try {
            MiniJson.parse(json);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Test
    @DisplayName("객체, 배열, 문자열, 숫자, true/false/null을 읽는다")
    void parsesBasicTypes() {
        Map<String, Object> o = MiniJson.asMap(MiniJson.parse(
                " { \"s\" : \"abc\", \"n\": -12.5e1, \"i\": 7, \"t\": true, \"f\": false, \"z\": null, \"a\": [1, \"x\", [ ]], \"o\": {} } "));
        assertThat(MiniJson.asString(o.get("s"))).isEqualTo("abc");
        assertThat(MiniJson.asDouble(o.get("n"), 0) == -125.0).isTrue();
        assertThat((int) MiniJson.asDouble(o.get("i"), 0)).isEqualTo(7);
        assertThat(MiniJson.asBoolean(o.get("t"), false)).isTrue();
        assertThat(MiniJson.asBoolean(o.get("f"), true)).isFalse();
        assertThat(o.containsKey("z") && o.get("z") == null).isTrue();
        List<Object> a = MiniJson.asList(o.get("a"));
        assertThat(a.size()).isEqualTo(3);
        assertThat(MiniJson.asList(a.get(2)).isEmpty()).isTrue();
        assertThat(MiniJson.asMap(o.get("o")).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("이스케이프와 유니코드(한글, 이모지 같은 짝 문자)를 읽는다")
    void parsesEscapes() {
        assertThat(MiniJson.asString(MiniJson.parse("\"\\uD55C\\uAE00 \\\"q\\\" \\\\ \\/ \\n\\t\""))).isEqualTo("한글 \"q\" \\ / \n\t");
        assertThat(MiniJson.asString(MiniJson.parse("\"\\uD83D\\uDE00\"")).codePointAt(0)).isEqualTo(0x1F600);
        assertThat(MiniJson.asString(MiniJson.parse("\"타이레놀정500mg\""))).isEqualTo("타이레놀정500mg");
    }

    @Test
    @DisplayName("형식이 틀리면 거절한다")
    void rejectsMalformed() {
        for (String bad : new String[]{"", "{", "[1,]", "{\"a\":}", "{\"a\" 1}", "tru", "\"abc", "{} x", "[1 2]", "{\"a\":1,}",
                "nul", "-", "1.", "1e", "\"\\u12G4\"", "\"\\x\"", "{'a':1}", "[\"a\u0001b\"]"}) {
            assertThat(rejects(bad)).as("거절해야 함: [" + bad + "]").isTrue();
        }
        assertThat(rejects(null)).isTrue();
    }

    @Test
    @DisplayName("너무 깊은 중첩은 거절한다 (스택이 넘치지 않게)")
    void rejectsTooDeep() {
        assertThat(rejects("[".repeat(100) + "]".repeat(100))).isTrue();
        assertThat(rejects("[".repeat(60) + "]".repeat(60))).isFalse();
    }

    @Test
    @DisplayName("오류 메시지에 입력 내용이 들어가지 않는다 (처방전 글자가 로그에 남지 않게)")
    void errorMessageHasNoInput() {
        try {
            MiniJson.parse("{\"inferText\":\"홍길동 900101-1234567\" BROKEN");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("홍길동")).isFalse();
            assertThat(e.getMessage().contains("900101")).isFalse();
            return;
        }
        throw new AssertionError("예외가 나야 함");
    }

    @Test
    @DisplayName("타입이 다른 값은 기본값을 돌려준다")
    void accessorsAreLenient() {
        assertThat(MiniJson.asMap("x") == null).isTrue();
        assertThat(MiniJson.asList(1.0) == null).isTrue();
        assertThat(MiniJson.asString(1.0) == null).isTrue();
        assertThat(MiniJson.asDouble("x", 3.5) == 3.5).isTrue();
        assertThat(MiniJson.asBoolean("x", true)).isTrue();
    }
}