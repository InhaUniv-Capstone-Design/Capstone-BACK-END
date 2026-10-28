package com.gamcho.yakyeon.domain.notification.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 푸시 알림용 FCM 토큰 (push_token) */
@Repository
public class PushTokenRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PushTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 토큰을 등록하거나 갱신한다. 같은 토큰이 이미 있으면(같은 기기에서 다른 계정이 로그인한 경우 포함)
     * 지금 로그인한 계정의 것으로 바꾼다 - 한 기기의 알림이 이전 계정으로 계속 가지 않게 한다.
     */
    public void upsert(long userId, String fcmToken, boolean notificationPermitted) {
        jdbc.update("INSERT INTO push_token (user_id, fcm_token, notification_permitted) VALUES (:me, :token, :permitted) "
                        + "ON CONFLICT (fcm_token) DO UPDATE SET user_id = EXCLUDED.user_id, "
                        + "notification_permitted = EXCLUDED.notification_permitted, updated_at = now()",
                new MapSqlParameterSource().addValue("me", userId).addValue("token", fcmToken)
                        .addValue("permitted", notificationPermitted));
    }

    /** 내 토큰만 지운다. 없거나 남의 토큰이어도 아무것도 지우지 않고 조용히 끝난다 (토큰 존재 여부를 알려주지 않는다) */
    public void delete(long userId, String fcmToken) {
        jdbc.update("DELETE FROM push_token WHERE user_id = :me AND fcm_token = :token",
                new MapSqlParameterSource().addValue("me", userId).addValue("token", fcmToken));
    }
}