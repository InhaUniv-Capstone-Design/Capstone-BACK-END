package com.gamcho.yakyeon.domain.account.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로그인 연속 실패 횟수와 잠금 (app_user.failed_login_count, locked_until).
 * 여러 요청이 동시에 틀려도 횟수가 빠지지 않도록 한 문장의 UPDATE로 올리고(행 잠금으로 순서대로 처리된다),
 * 시각 비교도 DB 시계(now())로 한다(서버와 DB의 시간대가 달라도 어긋나지 않게).
 */
@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

    static final String IS_LOCKED_SQL =
            "SELECT COALESCE(locked_until > now(), FALSE) FROM app_user WHERE user_id = :id";

    /**
     * 실패 1회를 올린다. 횟수가 한도에 이르면 잠금(locked_until)을 걸고 횟수를 0으로 되돌린다
     * (잠금이 풀린 뒤 처음부터 다시 센다). 돌려주는 값은 "이 실패로 지금 잠겼는가".
     */
    static final String RECORD_FAILURE_SQL =
            "UPDATE app_user SET "
                    + "failed_login_count = CASE WHEN failed_login_count + 1 >= :max THEN 0 ELSE failed_login_count + 1 END, "
                    + "locked_until = CASE WHEN failed_login_count + 1 >= :max "
                    + "THEN now() + (:minutes * INTERVAL '1 minute') ELSE locked_until END "
                    + "WHERE user_id = :id AND status = 'ACTIVE' "
                    + "RETURNING (locked_until IS NOT NULL AND locked_until > now()) AS locked";

    private final NamedParameterJdbcTemplate jdbc;

    /** 지금 잠겨 있는가 (없는 계정이면 false) */
    public boolean isLocked(long userId) {
        return jdbc.query(IS_LOCKED_SQL, new MapSqlParameterSource("id", userId), (rs, n) -> rs.getBoolean(1))
                .stream().findFirst().orElse(false);
    }

    /** 로그인 실패를 기록한다. 이 실패로 계정이 잠겼으면 true */
    public boolean recordFailure(long userId, int maxFailures, int lockMinutes) {
        return jdbc.query(RECORD_FAILURE_SQL,
                        new MapSqlParameterSource().addValue("id", userId).addValue("max", maxFailures)
                                .addValue("minutes", lockMinutes),
                        (rs, n) -> rs.getBoolean(1))
                .stream().findFirst().orElse(false);
    }
}