package com.gamcho.yakyeon.domain.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;
import com.gamcho.yakyeon.domain.notification.push.PushMessage;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmJsonTest {

    private static final String TOKEN = "fcm-token-abcdefghijklmnopqrstuvwxyz:0123456789_-";

    private static Map<String, Object> message(PushMessage m, int ttl) {
        return MiniJson.asMap(MiniJson.asMap(MiniJson.parse(FcmJson.message(m, ttl))).get("message"));
    }

    @Test
    @DisplayName("프론트와 정한 형식 그대로: token, data(title, body, notification_id), android(priority, ttl)")
    void matchesTheAgreedFormat() {
        Map<String, Object> message = message(new PushMessage(TOKEN, "복용 알림", "아침 약을 복용할 시간입니다.", "INTAKE", 12345L), 3600);
        assertThat(MiniJson.asString(message.get("token"))).isEqualTo(TOKEN);
        Map<String, Object> data = MiniJson.asMap(message.get("data"));
        assertThat(data.size()).as("데이터 키는 세 개뿐").isEqualTo(3);
        assertThat(MiniJson.asString(data.get("title"))).isEqualTo("복용 알림");
        assertThat(MiniJson.asString(data.get("body"))).isEqualTo("아침 약을 복용할 시간입니다.");
        assertThat(MiniJson.asString(data.get("notification_id"))).isEqualTo("12345");
        Map<String, Object> android = MiniJson.asMap(message.get("android"));
        assertThat(MiniJson.asString(android.get("priority"))).isEqualTo("high");
        assertThat(MiniJson.asString(android.get("ttl"))).isEqualTo("3600s");
        assertThat(message.containsKey("notification")).as("알림 창은 앱이 만든다: notification 블록이 없어야 함").isFalse();
        assertThat(message.size()).as("token, data, android 외에 다른 키가 없다").isEqualTo(3);
    }

    @Test
    @DisplayName("data의 값은 모두 문자열이다 (숫자를 넣으면 FCM이 400으로 거절한다)")
    void allDataValuesAreStrings() {
        Map<String, Object> data = MiniJson.asMap(message(new PushMessage(TOKEN, "t", "b", "X", Long.MAX_VALUE), 60).get("data"));
        for (Map.Entry<String, Object> e : data.entrySet()) {
            assertThat(e.getValue() instanceof String).as(e.getKey() + "는 문자열").isTrue();
        }
        assertThat(MiniJson.asString(data.get("notification_id"))).isEqualTo("9223372036854775807");
    }

    @Test
    @DisplayName("따옴표, 역슬래시, 줄바꿈, 제어 문자, 이모지가 들어 있어도 올바른 JSON이고 값이 그대로 돌아온다")
    void escapesSpecialCharacters() {
        String title = "약 \"먹을\" 시간\\n";
        String body = "첫 줄\n둘째 줄\t탭\r끝 \u0001 \u2028 😀";
        Map<String, Object> data = MiniJson.asMap(message(new PushMessage(TOKEN, title, body, "X", 1L), 60).get("data"));
        assertThat(MiniJson.asString(data.get("title"))).isEqualTo(title);
        assertThat(MiniJson.asString(data.get("body"))).isEqualTo(body);
    }

    @Test
    @DisplayName("문구가 비어 있어도(null) 오류 없이 빈 문자열로 보낸다")
    void nullTextBecomesEmpty() {
        Map<String, Object> data = MiniJson.asMap(message(new PushMessage(TOKEN, null, null, "X", 7L), 60).get("data"));
        assertThat(MiniJson.asString(data.get("title"))).isEqualTo("");
        assertThat(MiniJson.asString(data.get("body"))).isEqualTo("");
    }

    @Test
    @DisplayName("제어 문자는 \\u 이스케이프로 나간다")
    void quotesControlCharacters() {
        assertThat(FcmJson.quote("a\u0001b")).isEqualTo("\"a\\u0001b\"");
        assertThat(FcmJson.quote("\"")).isEqualTo("\"\\\"\"");
        assertThat(FcmJson.quote("한글")).isEqualTo("\"한글\"");
        assertThat(FcmJson.quote(null)).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("약 이름이 들어갈 자리가 없다: PushMessage에 있는 값(토큰, 제목, 본문, 알림 번호)만 나간다")
    void onlyGenericFieldsAreSent() {
        String json = FcmJson.message(new PushMessage(TOKEN, "복용 알림", "복용할 시간이에요", "INTAKE", 5L), 60);
        assertThat(json.contains("INTAKE")).as("내부 채널 이름도 나가지 않는다 (앱이 채널을 정함)").isFalse();
    }
}