package com.gamcho.yakyeon.domain.notification.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 푸시 발송 SQL (notification_delivery). 발송 시도 한 번이 한 행이다: 시도마다 새 행을 남겨서 기록이 이력으로 남고,
 * 재시도할 행(FAILED + next_retry_at)은 다음 시도를 한 뒤 next_retry_at을 비워서 다시 잡히지 않게 한다.
 *
 * 푸시를 받을 수 있는 조건(모든 조회에 공통):
 *  - 아직 읽지 않은 알림, 알림 권한이 켜진 기기, 받는 사람이 그 복약자를 지금도 볼 수 있음(본인 또는 ACTIVE 연동)
 *  - 24시간이 지나지 않은 알림
 *  - 그 기기의 토큰을 등록한 뒤에 만들어진 알림 (새 기기로 로그인하면 지난 알림이 한꺼번에 울리지 않게)
 */
@Repository
public class PushDeliveryRepository {

    /** 알림 받는 사람(n.recipient_user_id)이 그 복약자(p)를 지금도 볼 수 있는 조건 */
    private static final String RECIPIENT_CAN_SEE =
            "(p.user_id = n.recipient_user_id OR EXISTS (SELECT 1 FROM guardian_link g WHERE g.patient_id = n.patient_id "
                    + "AND g.guardian_user_id = n.recipient_user_id AND g.status = 'ACTIVE'))";

    /** 아직 이 기기로 한 번도 보내지 않은 알림 */
    public record PendingPush(long notificationId, String title, String body, String channel,
                              long pushTokenId, String fcmToken) {
    }

    /** 재시도할 차례가 온 실패 기록 (토큰이 이미 지워졌다면 pushTokenId와 fcmToken이 null) */
    public record RetryPush(long deliveryId, long notificationId, Long pushTokenId, int attemptNo, String title, String body,
                            String channel, OffsetDateTime createdAt, boolean unread, String fcmToken,
                            boolean tokenPermitted, boolean recipientCanStillSee) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PushDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PendingPush> findUndelivered(OffsetDateTime cutoff, int limit) {
        return jdbc.query("SELECT n.notification_id, n.title, n.body, n.android_channel, t.push_token_id, t.fcm_token "
                        + "FROM notification n JOIN patient p ON p.patient_id = n.patient_id "
                        + "JOIN push_token t ON t.user_id = n.recipient_user_id AND t.notification_permitted "
                        + "WHERE n.read_at IS NULL AND n.created_at > :cutoff AND n.created_at >= t.updated_at "
                        + "AND " + RECIPIENT_CAN_SEE + " "
                        + "AND NOT EXISTS (SELECT 1 FROM notification_delivery d "
                        + "WHERE d.notification_id = n.notification_id AND d.push_token_id = t.push_token_id) "
                        + "ORDER BY n.created_at, n.notification_id, t.push_token_id LIMIT :limit",
                new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("limit", limit),
                (rs, n) -> new PendingPush(rs.getLong("notification_id"), rs.getString("title"), rs.getString("body"),
                        rs.getString("android_channel"), rs.getLong("push_token_id"), rs.getString("fcm_token")));
    }

    public List<RetryPush> findDueRetries(OffsetDateTime now, int limit) {
        return jdbc.query("SELECT d.delivery_id, d.notification_id, d.push_token_id, d.attempt_no, n.title, n.body, "
                        + "n.android_channel, n.created_at, (n.read_at IS NULL) AS unread, t.fcm_token, "
                        + "coalesce(t.notification_permitted, false) AS permitted, "
                        + "(p.patient_id IS NOT NULL AND " + RECIPIENT_CAN_SEE + ") AS can_see "
                        + "FROM notification_delivery d JOIN notification n ON n.notification_id = d.notification_id "
                        + "LEFT JOIN patient p ON p.patient_id = n.patient_id "
                        + "LEFT JOIN push_token t ON t.push_token_id = d.push_token_id "
                        + "WHERE d.status = 'FAILED' AND d.next_retry_at IS NOT NULL AND d.next_retry_at <= :now "
                        + "ORDER BY d.next_retry_at, d.delivery_id LIMIT :limit",
                new MapSqlParameterSource().addValue("now", now).addValue("limit", limit),
                (rs, n) -> new RetryPush(rs.getLong("delivery_id"), rs.getLong("notification_id"),
                        (Long) rs.getObject("push_token_id"), rs.getInt("attempt_no"), rs.getString("title"),
                        rs.getString("body"), rs.getString("android_channel"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getBoolean("unread"),
                        rs.getString("fcm_token"), rs.getBoolean("permitted"), rs.getBoolean("can_see")));
    }

    /** 발송 시도 한 번을 기록한다. 오류에는 사유 코드만 남기고 토큰이나 알림 내용은 남기지 않는다 */
    public void insertAttempt(long notificationId, Long pushTokenId, String status, int attemptNo, String fcmMessageId,
                              String errorMessage, OffsetDateTime nextRetryAt, OffsetDateTime attemptedAt) {
        jdbc.update("INSERT INTO notification_delivery (notification_id, push_token_id, status, attempt_no, "
                        + "fcm_message_id, error_message, next_retry_at, attempted_at) "
                        + "VALUES (:nid, :tid, :status, :attempt, :msgId, :error, :next, :at)",
                new MapSqlParameterSource().addValue("nid", notificationId).addValue("tid", pushTokenId)
                        .addValue("status", status).addValue("attempt", attemptNo).addValue("msgId", fcmMessageId)
                        .addValue("error", errorMessage).addValue("next", nextRetryAt).addValue("at", attemptedAt));
    }

    /** 이 실패 기록은 다음 시도를 마쳤거나 더는 보내지 않기로 했으니 다시 잡히지 않게 한다 */
    public void clearRetry(long deliveryId) {
        jdbc.update("UPDATE notification_delivery SET next_retry_at = NULL WHERE delivery_id = :id",
                new MapSqlParameterSource("id", deliveryId));
    }

    /** 무효로 확인된 토큰을 지운다. 발송 기록은 남고 토큰 번호만 비워진다(DB 규칙) */
    public void deleteToken(long pushTokenId) {
        jdbc.update("DELETE FROM push_token WHERE push_token_id = :id", new MapSqlParameterSource("id", pushTokenId));
    }
}