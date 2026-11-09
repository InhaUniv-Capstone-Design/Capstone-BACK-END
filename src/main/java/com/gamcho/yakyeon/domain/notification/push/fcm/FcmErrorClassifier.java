package com.gamcho.yakyeon.domain.notification.push.fcm;

import com.gamcho.yakyeon.domain.medication.ocr.clova.MiniJson;
import com.gamcho.yakyeon.domain.notification.push.PushResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FCM HTTP v1의 응답을 발송 결과로 분류한다 (스프링·네트워크 없는 순수 로직). 오류 코드의 뜻은 Firebase 문서(FCM 오류 코드) 기준이다.
 *
 *  200                          → SENT (응답의 name에서 메시지 번호)
 *  UNREGISTERED (404)           → INVALID_TOKEN: 앱을 지웠거나 토큰이 만료됨. 이 토큰은 지우고 다시 쓰지 않는다.
 *  INVALID_ARGUMENT(400) 중 "registration token" 오류 → INVALID_TOKEN: 토큰 형식이 올바르지 않음.
 *  그 밖의 모든 오류               → TRANSIENT_FAILURE (나중에 다시 시도, 5번째 실패에서 포기)
 *
 * 토큰을 지우는 경우를 일부러 좁게 잡았다. INVALID_ARGUMENT(우리 요청 형식 문제일 수도 있다), SENDER_ID_MISMATCH(서버의 프로젝트 설정이
 * 틀렸을 수도 있다), 인증 오류처럼 "서버 쪽 잘못"일 수 있는 오류에서 토큰을 지우면, 설정 하나가 틀렸을 때 모든 사용자의 토큰이
 * 한꺼번에 사라지고 사용자가 다시 로그인해야 푸시를 받는다. 일시 실패로 두면 재시도만 하고 토큰은 남는다.
 * 오류 응답의 메시지 본문은 로그에 남기지 않는다(4자리 코드와 HTTP 상태만).
 */
public final class FcmErrorClassifier {

    private static final int MAX_MESSAGE_ID = 200;

    private FcmErrorClassifier() {
    }

    public static PushResult classify(int httpStatus, String body) {
        Map<String, Object> root = parse(body);
        if (httpStatus >= 200 && httpStatus < 300) {
            return PushResult.sent(messageId(root));
        }
        Map<String, Object> error = root == null ? null : MiniJson.asMap(root.get("error"));
        String fcmCode = fcmErrorCode(error);
        String status = error == null ? null : MiniJson.asString(error.get("status"));
        String message = error == null ? "" : String.valueOf(MiniJson.asString(error.get("message"))).toLowerCase(Locale.ROOT);

        if ("UNREGISTERED".equals(fcmCode) || "UNREGISTERED".equals(status)) {
            return PushResult.invalidToken();
        }
        if (("INVALID_ARGUMENT".equals(fcmCode) || "INVALID_ARGUMENT".equals(status)) && message.contains("registration token")) {
            return PushResult.invalidToken();
        }
        return PushResult.transientFailure(shortCode(fcmCode != null ? fcmCode : status, httpStatus));
    }

    private static Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MiniJson.asMap(MiniJson.parse(body));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 성공 응답의 name("projects/프로젝트/messages/번호")에서 번호 */
    private static String messageId(Map<String, Object> root) {
        String name = root == null ? null : MiniJson.asString(root.get("name"));
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String id = name.substring(name.lastIndexOf('/') + 1);
        return id.length() > MAX_MESSAGE_ID ? id.substring(0, MAX_MESSAGE_ID) : id;
    }

    /** error.details 중 FcmError 형식 항목의 errorCode (예: UNREGISTERED, SENDER_ID_MISMATCH) */
    private static String fcmErrorCode(Map<String, Object> error) {
        List<Object> details = error == null ? null : MiniJson.asList(error.get("details"));
        if (details == null) {
            return null;
        }
        for (Object d : details) {
            Map<String, Object> detail = MiniJson.asMap(d);
            String code = detail == null ? null : MiniJson.asString(detail.get("errorCode"));
            if (code != null) {
                return code;
            }
        }
        return null;
    }

    /** 기록 열에 넣을 짧은 오류 이름. 대문자와 밑줄만 남기고 40자로 자른다. 쓸 수 있는 것이 없으면 HTTP 상태 */
    private static String shortCode(String code, int httpStatus) {
        if (code != null && code.matches("[A-Z_]{1,40}")) {
            return code;
        }
        return "HTTP_" + httpStatus;
    }
}