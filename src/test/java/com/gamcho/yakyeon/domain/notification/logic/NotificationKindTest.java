package com.gamcho.yakyeon.domain.notification.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림 종류가 DB의 CHECK 제약(notification.notif_type, android_channel)과 어긋나면 서버를 켠 뒤 알림을 만드는 순간에야
 * DB 오류가 난다. 그걸 테스트로 미리 막는다. DB 제약이 바뀌면 이 목록도 같이 고친다.
 */
class NotificationKindTest {

    private static final List<String> DB_TYPES = List.of(
            "INTERACTION_INFO", "INTAKE_DETECTED", "MISSED_DOSE", "LOW_STOCK",
            "DEVICE_STATUS", "DOSE_REMINDER", "END_SOON", "DEVICE_OPENED");
    private static final List<String> DB_CHANNELS = List.of("INTERACTION", "INTAKE", "DEVICE", "STOCK");

    @Test
    @DisplayName("모든 알림 종류의 저장 값과 채널이 DB가 허용하는 값이다")
    void everyKindIsAllowedByDb() {
        for (NotificationKind kind : NotificationKind.values()) {
            assertThat(DB_TYPES.contains(kind.type())).as(kind + " 종류 " + kind.type()).isTrue();
            assertThat(DB_CHANNELS.contains(kind.channel())).as(kind + " 채널 " + kind.channel()).isTrue();
            assertThat(kind.title().length() <= 100).as(kind + " 제목 길이(DB 100자)").isTrue();
        }
    }

    @Test
    @DisplayName("본인용 미복용 알림은 보호자용과 같은 MISSED_DOSE로 저장되지만 문구가 다르다")
    void selfMissedDoseIsStoredAsMissedDose() {
        assertThat(NotificationKind.MISSED_DOSE_SELF.type()).isEqualTo("MISSED_DOSE");
        assertThat(NotificationKind.MISSED_DOSE.type()).isEqualTo("MISSED_DOSE");
        assertThat(NotificationKind.MISSED_DOSE_SELF.title().equals(NotificationKind.MISSED_DOSE.title())).isFalse();
    }

    @Test
    @DisplayName("복용 시간 알림은 DOSE_REMINDER, 복용 채널로 저장된다")
    void doseReminder() {
        assertThat(NotificationKind.DOSE_REMINDER.type()).isEqualTo("DOSE_REMINDER");
        assertThat(NotificationKind.DOSE_REMINDER.channel()).isEqualTo("INTAKE");
    }

    @Test
    @DisplayName("문구에 약 이름 같은 건강정보 자리표시가 없다")
    void textHasNoPlaceholders() {
        for (NotificationKind kind : NotificationKind.values()) {
            assertThat(kind.body().contains("%") || kind.body().contains("{")).as(kind + " 본문").isFalse();
        }
    }
}