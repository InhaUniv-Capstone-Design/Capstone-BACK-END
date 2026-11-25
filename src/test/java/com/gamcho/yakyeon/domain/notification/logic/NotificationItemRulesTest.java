package com.gamcho.yakyeon.domain.notification.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationItemRulesTest {

    @Test
    @DisplayName("복용 확인·미복용은 복약 기록, 약통 열림·이상·약 부족은 약통 이벤트, 병용금기는 병용금기 정보다")
    void mapping() {
        assertThat(NotificationItemRules.requiredItem("INTAKE_DETECTED")).isEqualTo("DOSE_RECORD");
        assertThat(NotificationItemRules.requiredItem("MISSED_DOSE")).isEqualTo("DOSE_RECORD");
        assertThat(NotificationItemRules.requiredItem("DEVICE_OPENED")).isEqualTo("DEVICE_EVENT");
        assertThat(NotificationItemRules.requiredItem("DEVICE_STATUS")).isEqualTo("DEVICE_EVENT");
        assertThat(NotificationItemRules.requiredItem("LOW_STOCK")).isEqualTo("DEVICE_EVENT");
        assertThat(NotificationItemRules.requiredItem("INTERACTION_INFO")).isEqualTo("DUR_INFO");
    }

    @Test
    @DisplayName("복약자 본인에게만 가는 알림(복용 시간, 복용 종료 예정)과 모르는 종류에는 항목이 없다")
    void selfOnlyAndUnknownHaveNoItem() {
        assertThat(NotificationItemRules.requiredItem("DOSE_REMINDER") == null).isTrue();
        assertThat(NotificationItemRules.requiredItem("END_SOON") == null).isTrue();
        assertThat(NotificationItemRules.requiredItem("SOMETHING") == null).isTrue();
        assertThat(NotificationItemRules.requiredItem(null) == null).isTrue();
    }

    @Test
    @DisplayName("모든 항목은 6개 열람 항목 안에 있고 사진(PHOTO)은 없다")
    void itemsAreValid() {
        for (String type : new String[]{"INTAKE_DETECTED", "MISSED_DOSE", "DEVICE_OPENED", "DEVICE_STATUS", "LOW_STOCK", "INTERACTION_INFO"}) {
            String item = NotificationItemRules.requiredItem(type);
            assertThat(SharedItemRules.ALL.contains(item)).as(type).isTrue();
            assertThat(item.equals("PHOTO")).as(type).isFalse();
        }
    }

    @Test
    @DisplayName("SQL CASE 식은 6가지 종류를 모두 담고, 항상 같은 순서로 만들어진다")
    void caseSql() {
        String sql = NotificationItemRules.caseSql("n.notif_type");
        assertThat(sql.startsWith("CASE n.notif_type WHEN ")).isTrue();
        assertThat(sql.endsWith(" END")).isTrue();
        for (String pair : new String[]{"'INTAKE_DETECTED' THEN 'DOSE_RECORD'", "'MISSED_DOSE' THEN 'DOSE_RECORD'",
                "'DEVICE_OPENED' THEN 'DEVICE_EVENT'", "'DEVICE_STATUS' THEN 'DEVICE_EVENT'",
                "'LOW_STOCK' THEN 'DEVICE_EVENT'", "'INTERACTION_INFO' THEN 'DUR_INFO'"}) {
            assertThat(sql.contains(pair)).as(pair).isTrue();
        }
        assertThat(sql.contains("DOSE_REMINDER")).as("본인 전용 종류는 없다").isFalse();
        assertThat(NotificationItemRules.caseSql("n.notif_type")).isEqualTo(sql);
        assertThat(NotificationItemRules.caseSql("x.t").startsWith("CASE x.t WHEN ")).isTrue();
    }
}