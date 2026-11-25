package com.gamcho.yakyeon.domain.notification.repository;

import com.gamcho.yakyeon.domain.notification.logic.NotificationSettingKey;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/** 알림 켜기/끄기 설정 (notification_setting). 사용자가 바꾼 항목만 행이 있고, 행이 없으면 켜짐이다. */
@Repository
public class NotificationSettingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationSettingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 저장된 설정(문자열 키). 바꾼 적 없는 항목은 들어 있지 않다 */
    public Map<String, Boolean> findAll(long userId) {
        Map<String, Boolean> result = new HashMap<>();
        jdbc.query("SELECT setting_key, enabled FROM notification_setting WHERE user_id = :me",
                new MapSqlParameterSource("me", userId),
                rs -> {
                    result.put(rs.getString("setting_key"), rs.getBoolean("enabled"));
                });
        return result;
    }

    public void upsert(long userId, NotificationSettingKey key, boolean enabled) {
        jdbc.update("INSERT INTO notification_setting (user_id, setting_key, enabled) VALUES (:me, :key, :enabled) "
                        + "ON CONFLICT (user_id, setting_key) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now()",
                new MapSqlParameterSource().addValue("me", userId).addValue("key", key.name()).addValue("enabled", enabled));
    }
}