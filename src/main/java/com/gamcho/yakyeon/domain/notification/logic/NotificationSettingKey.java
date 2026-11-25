package com.gamcho.yakyeon.domain.notification.logic;

/**
 * 알림 켜기/끄기 설정의 종류. DB의 CHECK 제약(notification_setting.setting_key)과 같아야 한다.
 *
 * 같은 사건도 받는 사람이 본인이면 일반 키, 연동된 보호자면 GUARDIAN_ 키로 판단한다.
 *  "보호자 공유 알림" 화면: GUARDIAN_OPEN(약통 열림), GUARDIAN_TAKEN(복용), GUARDIAN_MISSED(미복용), GUARDIAN_DEVICE(기기 이상)
 *  "알림 설정" 화면(본인용): DOSE_REMINDER, MISSED, END_SOON, DEVICE
 */
public enum NotificationSettingKey {
    DOSE_REMINDER,
    MISSED,
    END_SOON,
    DEVICE,
    GUARDIAN_OPEN,
    GUARDIAN_TAKEN,
    GUARDIAN_MISSED,
    GUARDIAN_DEVICE
}