package com.gamcho.yakyeon.domain.notification.push;

/**
 * 푸시 한 건. 푸시는 잠금 화면에 보이고 Google 서버를 거치므로 알림의 일반 문구(title, body)와
 * 알림 번호(앱이 상세를 가져올 때 쓴다)만 담는다. 약 이름이나 병명은 담지 않는다.
 *
 * @param channel 안드로이드 알림 채널 (INTERACTION / INTAKE / DEVICE / STOCK)
 */
public record PushMessage(String fcmToken, String title, String body, String channel, long notificationId) {
}