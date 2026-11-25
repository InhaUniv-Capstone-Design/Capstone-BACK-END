package com.gamcho.yakyeon.domain.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.notification.push.PushResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmErrorClassifierTest {

    private static String error(int code, String status, String fcmCode, String message) {
        String details = fcmCode == null ? "" : ",\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"" + fcmCode + "\"}]";
        return "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\",\"status\":\"" + status + "\"" + details + "}}";
    }

    private static PushResult classify(int http, String body) {
        return FcmErrorClassifier.classify(http, body);
    }

    @Test
    @DisplayName("성공(200): 응답의 name에서 메시지 번호를 꺼낸다")
    void success() {
        PushResult r = classify(200, "{\"name\":\"projects/yakyeon/messages/0:1700000000000000%abc\"}");
        assertThat(r.status()).isEqualTo(PushResult.Status.SENT);
        assertThat(r.messageId()).isEqualTo("0:1700000000000000%abc");
        assertThat(classify(200, "{}").messageId()).isEqualTo("unknown");
        assertThat(classify(200, "").status()).isEqualTo(PushResult.Status.SENT);
        assertThat(classify(200, "{\"name\":\"" + "x".repeat(500) + "\"}").messageId().length()).as("DB 열은 200자").isEqualTo(200);
    }

    @Test
    @DisplayName("UNREGISTERED(404)는 무효 토큰이다: 앱을 지웠거나 토큰이 만료됨")
    void unregisteredIsInvalidToken() {
        assertThat(classify(404, error(404, "NOT_FOUND", "UNREGISTERED", "Requested entity was not found.")).status()).isEqualTo(PushResult.Status.INVALID_TOKEN);
        assertThat(classify(404, "{\"error\":{\"status\":\"UNREGISTERED\"}}").status()).isEqualTo(PushResult.Status.INVALID_TOKEN);
    }

    @Test
    @DisplayName("등록 토큰 형식 오류(INVALID_ARGUMENT + registration token 메시지)는 무효 토큰이다")
    void malformedRegistrationTokenIsInvalidToken() {
        assertThat(classify(400, error(400, "INVALID_ARGUMENT", null, "The registration token is not a valid FCM registration token")).status())
                .isEqualTo(PushResult.Status.INVALID_TOKEN);
    }

    @Test
    @DisplayName("그 밖의 INVALID_ARGUMENT(우리 요청 형식 문제일 수 있음)는 토큰을 지우지 않고 일시 실패로 둔다")
    void otherInvalidArgumentIsTransient() {
        PushResult r = classify(400, error(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT", "Invalid value at 'message.data[0].value' (TYPE_STRING), 12"));
        assertThat(r.status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
        assertThat(r.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("서버 쪽 설정 오류일 수 있는 SENDER_ID_MISMATCH(403)도 토큰을 지우지 않는다 (모든 사용자의 토큰이 한꺼번에 사라지는 사고 방지)")
    void senderIdMismatchKeepsTokens() {
        PushResult r = classify(403, error(403, "PERMISSION_DENIED", "SENDER_ID_MISMATCH", "SenderId mismatch"));
        assertThat(r.status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
        assertThat(r.errorCode()).isEqualTo("SENDER_ID_MISMATCH");
    }

    @Test
    @DisplayName("한도 초과(429), 서버 바쁨(503), 내부 오류(500)는 일시 실패다")
    void transientErrors() {
        assertThat(classify(429, error(429, "RESOURCE_EXHAUSTED", "QUOTA_EXCEEDED", "Quota exceeded")).errorCode()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(classify(503, error(503, "UNAVAILABLE", "UNAVAILABLE", "The service is currently unavailable.")).errorCode()).isEqualTo("UNAVAILABLE");
        assertThat(classify(500, error(500, "INTERNAL", "INTERNAL", "Internal error")).status()).isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
    }

    @Test
    @DisplayName("FCM 상세 코드가 없으면 error.status를, 그것도 없으면 HTTP 상태를 오류 이름으로 쓴다")
    void fallbackNames() {
        assertThat(classify(401, "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}").errorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(classify(502, "<html>Bad Gateway</html>").errorCode()).isEqualTo("HTTP_502");
        assertThat(classify(500, "").errorCode()).isEqualTo("HTTP_500");
        assertThat(classify(500, null).errorCode()).isEqualTo("HTTP_500");
        assertThat(classify(404, "{\"error\":{\"status\":\"NOT_FOUND\"}}").status())
                .as("상세 코드(UNREGISTERED) 없는 404는 설정 오류일 수 있어 토큰을 지우지 않는다").isEqualTo(PushResult.Status.TRANSIENT_FAILURE);
    }

    @Test
    @DisplayName("오류 이름은 기록 열에 안전한 값만 쓴다 (대문자·밑줄, 40자 이내, 응답 본문 글자는 안 넣음)")
    void errorCodesAreSanitized() {
        PushResult r = classify(400, error(400, "INVALID_ARGUMENT", "bad value with spaces and 한글", "secret text"));
        assertThat(r.errorCode().matches("[A-Z_]{1,40}|HTTP_[0-9]+")).as("오류 이름: " + r.errorCode()).isTrue();
        assertThat(String.valueOf(r.errorCode()).contains("secret")).isFalse();
    }
}