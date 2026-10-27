package com.gamcho.yakyeon.domain.notification.repository;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.notification.dto.NotificationResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 알림 조회·읽음 처리 SQL.
 *
 * 받는 사람 본인의 알림이라도, 그 알림이 가리키는 복약자의 정보를 지금도 볼 수 있을 때만 보여준다:
 * 복약자 본인이거나, 활성(ACTIVE) 연동이 있는 보호자. 보호자가 연동을 해제하면 이미 받은 그 복약자의 알림도
 * 목록과 읽음 처리에서 바로 사라진다 (철회하면 권한이 즉시 끝난다는 원칙).
 */
@Repository
public class NotificationRepository {

    /** 알림(n)이 가리키는 복약자(p)를 받는 사람(:me)이 지금도 볼 수 있는 조건 */
    private static final String VISIBLE =
            "(p.user_id = :me OR EXISTS (SELECT 1 FROM guardian_link g WHERE g.patient_id = n.patient_id "
                    + "AND g.guardian_user_id = :me AND g.status = 'ACTIVE'))";

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationResponse> list(long userId, boolean unreadOnly, int size, int offset) {
        return jdbc.query("SELECT n.notification_id, n.patient_id, n.notif_type, n.title, n.body, n.created_at, n.read_at, "
                        + "n.scheduled_dose_id, n.intake_event_id, n.mapping_id, n.device_id, n.check_id "
                        + "FROM notification n JOIN patient p ON p.patient_id = n.patient_id "
                        + "WHERE n.recipient_user_id = :me AND " + VISIBLE
                        + (unreadOnly ? " AND n.read_at IS NULL" : "")
                        + " ORDER BY n.created_at DESC, n.notification_id DESC LIMIT :size OFFSET :offset",
                new MapSqlParameterSource().addValue("me", userId).addValue("size", size).addValue("offset", offset),
                (rs, n) -> {
                    OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
                    return new NotificationResponse(rs.getLong("notification_id"), rs.getLong("patient_id"),
                            rs.getString("notif_type"), rs.getString("title"), rs.getString("body"),
                            kst(rs.getObject("created_at", OffsetDateTime.class)), readAt != null, kst(readAt),
                            nullableLong(rs.getObject("scheduled_dose_id")), nullableLong(rs.getObject("intake_event_id")),
                            nullableLong(rs.getObject("mapping_id")), nullableLong(rs.getObject("device_id")),
                            nullableLong(rs.getObject("check_id")));
                });
    }

    public long count(long userId, boolean unreadOnly) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM notification n JOIN patient p ON p.patient_id = n.patient_id "
                        + "WHERE n.recipient_user_id = :me AND " + VISIBLE + (unreadOnly ? " AND n.read_at IS NULL" : ""),
                new MapSqlParameterSource("me", userId), Long.class);
        return count == null ? 0 : count;
    }

    /** 읽음 처리. 이미 읽은 알림이면 읽은 시각을 바꾸지 않고(같은 요청을 반복해도 결과가 같다) 성공으로 본다 */
    public boolean markRead(long userId, long notificationId, OffsetDateTime now) {
        return !jdbc.query("UPDATE notification AS n SET read_at = coalesce(n.read_at, :now) FROM patient p "
                                + "WHERE n.notification_id = :id AND n.recipient_user_id = :me AND p.patient_id = n.patient_id "
                                + "AND " + VISIBLE + " RETURNING n.notification_id",
                        new MapSqlParameterSource().addValue("me", userId).addValue("id", notificationId).addValue("now", now),
                        (rs, n) -> rs.getLong(1))
                .isEmpty();
    }

    /** 지금 볼 수 있는 읽지 않은 알림을 모두 읽음 처리하고 처리한 건수를 돌려준다 */
    public int markAllRead(long userId, OffsetDateTime now) {
        return jdbc.update("UPDATE notification AS n SET read_at = :now FROM patient p "
                        + "WHERE n.recipient_user_id = :me AND n.read_at IS NULL AND p.patient_id = n.patient_id AND " + VISIBLE,
                new MapSqlParameterSource().addValue("me", userId).addValue("now", now));
    }

    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime();
    }

    private static Long nullableLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}