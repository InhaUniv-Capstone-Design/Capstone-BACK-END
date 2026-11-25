package com.gamcho.yakyeon.domain.medication.ocr.clova;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 작은 JSON 해석기 (스프링·외부 라이브러리 없음). 외부 OCR의 응답처럼 크고 중첩된 JSON을 읽는 데만 쓴다.
 *
 * 결과는 Map(객체), List(배열), String, Double(숫자), Boolean, null 이다. 형식이 틀리면 IllegalArgumentException이고
 * 메시지에는 입력 내용을 담지 않는다(처방전 글자가 로그에 남지 않게). 중첩 깊이를 제한해 악의적인 입력으로 스택이 넘치지 않게 한다.
 */
public final class MiniJson {

    private static final int MAX_DEPTH = 64;

    private final String s;
    private int pos;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        if (json == null) {
            throw bad();
        }
        MiniJson p = new MiniJson(json);
        p.skipWhitespace();
        Object value = p.value(0);
        p.skipWhitespace();
        if (p.pos != p.s.length()) {
            throw bad();
        }
        return value;
    }

    // ---- 값을 꺼내는 도우미 (타입이 다르면 null 또는 기본값)

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String asString(Object o) {
        return o instanceof String ? (String) o : null;
    }

    public static double asDouble(Object o, double fallback) {
        return o instanceof Number ? ((Number) o).doubleValue() : fallback;
    }

    public static boolean asBoolean(Object o, boolean fallback) {
        return o instanceof Boolean ? (Boolean) o : fallback;
    }

    // ---- 해석

    private static IllegalArgumentException bad() {
        return new IllegalArgumentException("JSON 형식이 올바르지 않습니다.");
    }

    private void skipWhitespace() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH || pos >= s.length()) {
            throw bad();
        }
        char c = s.charAt(pos);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw bad();
        }
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, pos)) {
            throw bad();
        }
        pos += word.length();
        return value;
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;   // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw bad();
            }
            String key = string();
            skipWhitespace();
            if (peek() != ':') {
                throw bad();
            }
            pos++;
            skipWhitespace();
            map.put(key, value(depth + 1));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw bad();
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        pos++;   // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value(depth + 1));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw bad();
            }
        }
    }

    private char peek() {
        if (pos >= s.length()) {
            throw bad();
        }
        return s.charAt(pos);
    }

    private String string() {
        pos++;   // 여는 따옴표
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) {
                throw bad();
            }
            char c = s.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw bad();   // 문자열 안의 제어 문자는 JSON이 허용하지 않는다
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= s.length()) {
                throw bad();
            }
            char e = s.charAt(pos++);
            switch (e) {
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case '/':
                    sb.append('/');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (pos + 4 > s.length()) {
                        throw bad();
                    }
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = Character.digit(s.charAt(pos + i), 16);
                        if (digit < 0) {
                            throw bad();
                        }
                        code = code * 16 + digit;
                    }
                    pos += 4;
                    sb.append((char) code);
                    break;
                default:
                    throw bad();
            }
        }
    }

    private Double number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        int digits = 0;
        while (pos < s.length() && Character.isDigit(s.charAt(pos)) && s.charAt(pos) < 128) {
            pos++;
            digits++;
        }
        if (digits == 0) {
            throw bad();
        }
        if (pos < s.length() && s.charAt(pos) == '.') {
            pos++;
            int fraction = 0;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                pos++;
                fraction++;
            }
            if (fraction == 0) {
                throw bad();
            }
        }
        if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
            pos++;
            if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                pos++;
            }
            int exponent = 0;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                pos++;
                exponent++;
            }
            if (exponent == 0) {
                throw bad();
            }
        }
        try {
            return Double.parseDouble(s.substring(start, pos));
        } catch (NumberFormatException e) {
            throw bad();
        }
    }
}