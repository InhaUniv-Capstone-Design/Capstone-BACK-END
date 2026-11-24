package com.gamcho.yakyeon.domain.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;
import com.gamcho.yakyeon.domain.notification.push.DataPush;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmJsonDataPushTest {

    private static final String TOKEN = "fcm-token-abcdefghijklmnopqrstuvwxyz:0123456789_-";

    private static Map<String, Object> message(DataPush p, int ttl) {
        return MiniJson.asMap(MiniJson.asMap(MiniJson.parse(FcmJson.dataMessage(p, ttl))).get("message"));
    }

    @Test
    @DisplayName("앱과 정한 형식 그대로: token, data(type, signal_type, patient_id, compartment_no), android(priority high)")
    void matchesTheAgreedFormat() {
        Map<String, Object> message = message(new DataPush(TOKEN, DataPush.IR_TRIGGER, 10L, 2), 60);
        assertThat(MiniJson.asString(message.get("token"))).isEqualTo(TOKEN);
        Map<String, Object> data = MiniJson.asMap(message.get("data"));
        assertThat(data.keySet()).containsExactlyInAnyOrder("type", "signal_type", "patient_id", "compartment_no");
        assertThat(MiniJson.asString(data.get("type"))).isEqualTo("IR_TRIGGER");
        assertThat(MiniJson.asString(data.get("signal_type"))).isEqualTo("IR_TRIGGER");
        assertThat(MiniJson.asString(data.get("patient_id"))).isEqualTo("10");
        assertThat(MiniJson.asString(data.get("compartment_no"))).isEqualTo("2");
        Map<String, Object> android = MiniJson.asMap(message.get("android"));
        assertThat(MiniJson.asString(android.get("priority"))).isEqualTo("high");
        assertThat(MiniJson.asString(android.get("ttl"))).isEqualTo("60s");
    }

    @Test
    @DisplayName("알림 창이 뜨지 않게 notification, title, body, notification_id가 없다")
    void hasNoNotificationParts() {
        Map<String, Object> message = message(new DataPush(TOKEN, DataPush.IR_TRIGGER, 10L, 1), 60);
        assertThat(message.containsKey("notification")).isFalse();
        assertThat(message.size()).as("token, data, android 외에 다른 키가 없다").isEqualTo(3);
        Map<String, Object> data = MiniJson.asMap(message.get("data"));
        assertThat(data.containsKey("title")).isFalse();
        assertThat(data.containsKey("body")).isFalse();
        assertThat(data.containsKey("notification_id")).isFalse();
    }

    @Test
    @DisplayName("data의 값은 모두 문자열이다 (숫자를 넣으면 FCM이 400으로 거절한다)")
    void allDataValuesAreStrings() {
        Map<String, Object> data = MiniJson.asMap(message(new DataPush(TOKEN, DataPush.IR_TRIGGER, Long.MAX_VALUE, 3), 60).get("data"));
        for (Map.Entry<String, Object> e : data.entrySet()) {
            assertThat(e.getValue() instanceof String).as(e.getKey() + "는 문자열").isTrue();
        }
        assertThat(MiniJson.asString(data.get("patient_id"))).isEqualTo("9223372036854775807");
    }
}