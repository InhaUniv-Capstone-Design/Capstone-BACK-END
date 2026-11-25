package com.gamcho.yakyeon.domain.notification.logic;

import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;

import java.util.Map;
import java.util.TreeMap;

/**
 * 보호자에게 가는 알림이 복약자의 어떤 열람 항목에 속하는지 (스프링·DB 없는 순수 규칙).
 * 보호자는 복약자가 그 항목을 허용했을 때만 이 알림을 받고(만들어지고), 볼 수 있다(목록·읽음 처리).
 *
 *  - 복용 확인, 미복용                       → DOSE_RECORD
 *  - 약통 열림, 약통 이상, 약이 얼마 안 남음   → DEVICE_EVENT
 *  - 병용금기 새 등재                        → DUR_INFO
 *
 * 복용 시간 알림, 복용 종료 예정 알림은 복약자 본인에게만 가므로 여기에 없다.
 */
public final class NotificationItemRules {

    /** 알림 종류(notification.notif_type 값) → 필요한 열람 항목 */
    private static final Map<String, String> ITEM_BY_TYPE = new TreeMap<>(Map.of(
            "INTAKE_DETECTED", SharedItemRules.DOSE_RECORD,
            "MISSED_DOSE", SharedItemRules.DOSE_RECORD,
            "DEVICE_OPENED", SharedItemRules.DEVICE_EVENT,
            "DEVICE_STATUS", SharedItemRules.DEVICE_EVENT,
            "LOW_STOCK", SharedItemRules.DEVICE_EVENT,
            "INTERACTION_INFO", SharedItemRules.DUR_INFO));

    private NotificationItemRules() {
    }

    /** 이 알림 종류를 보호자가 받으려면 필요한 열람 항목. 보호자에게 가지 않는 종류면 null */
    public static String requiredItem(String notifType) {
        return notifType == null ? null : ITEM_BY_TYPE.get(notifType);
    }

    /**
     * SQL에서 알림 종류 열(typeColumn)을 필요한 열람 항목으로 바꾸는 CASE 식. 보호자에게 가지 않는 종류는 NULL이 되어
     * "항목을 허용했다"는 조건이 참이 되지 않는다(보이지 않는다). 항목 이름은 이 클래스의 고정 상수라서 SQL에 그대로 넣어도 안전하다.
     * typeColumn도 호출하는 코드의 고정 열 이름만 넘긴다(예: "n.notif_type").
     */
    public static String caseSql(String typeColumn) {
        StringBuilder sql = new StringBuilder("CASE ").append(typeColumn);
        for (Map.Entry<String, String> e : ITEM_BY_TYPE.entrySet()) {
            sql.append(" WHEN '").append(e.getKey()).append("' THEN '").append(e.getValue()).append("'");
        }
        return sql.append(" END").toString();
    }
}