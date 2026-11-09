package com.gamcho.yakyeon.domain.notification.push.fcm;

import com.gamcho.yakyeon.domain.notification.push.PushMessage;

/**
 * FCM HTTP v1 메시지 본문을 만든다 (스프링·네트워크 없는 순수 로직).
 *
 * 형식은 Android 앱(프론트)과 정한 계약이다:
 *   {"message":{"token":"기기 토큰",
 *               "data":{"title":"…","body":"…","notification_id":"12345"},
 *               "android":{"priority":"high","ttl":"3600s"}}}
 *  - data만 보낸다(notification 블록이 없다). 알림 창은 앱이 직접 만든다. 그래서 알림 채널도 앱이 정한다(yakyeon_default).
 *  - data의 값은 모두 문자열이어야 한다(숫자를 넣으면 FCM이 400 INVALID_ARGUMENT로 거절한다). notification_id도 문자열이다.
 *  - title과 body는 일반 문구뿐이다(잠금 화면에 보이고 구글 서버를 거치므로 약 이름·병명은 PushMessage에 애초에 없다).
 *  - priority high: 약 먹을 시간 알림은 바로 도착해야 해서 높은 우선순위로 보낸다.
 *  - ttl: 기기가 꺼져 있을 때 FCM이 보관하는 시간. 오래 지난 복약 알림은 혼란스러워서 짧게 둔다.
 */
public final class FcmJson {

    private FcmJson() {
    }

    public static String message(PushMessage m, int ttlSeconds) {
        return "{\"message\":{\"token\":" + quote(m.fcmToken())
                + ",\"data\":{\"title\":" + quote(m.title())
                + ",\"body\":" + quote(m.body())
                + ",\"notification_id\":" + quote(Long.toString(m.notificationId())) + "}"
                + ",\"android\":{\"priority\":\"high\",\"ttl\":\"" + ttlSeconds + "s\"}}}";
    }

    /** JSON 문자열로 만든다: 따옴표, 역슬래시, 제어 문자를 이스케이프하고 나머지(한글 등)는 그대로 둔다 */
    static String quote(String s) {
        String value = s == null ? "" : s;
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}