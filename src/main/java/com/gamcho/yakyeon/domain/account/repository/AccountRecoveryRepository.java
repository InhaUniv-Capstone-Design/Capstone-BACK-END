package com.gamcho.yakyeon.domain.account.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 아이디 찾기·비밀번호 재설정이 쓰는 조회와 수정 (활성 계정만 대상, 탈퇴한 계정은 이름·번호가 지워져 있어서 애초에 찾아지지 않는다).
 * 비밀번호 해시, 전화번호 원문, 로그인 토큰 값은 이 클래스의 결과로 밖에 나가지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AccountRecoveryRepository {

    /** 조회 결과 한 계정. phoneHash는 서비스 안에서 번호 일치를 확인하는 데만 쓴다 */
    public record Account(long userId, String loginId, String name, String accountType, String phoneHash) {
    }

    static final String FIND_BY_PHONE_SQL =
            "SELECT user_id, login_id, name, account_type, phone_hash FROM app_user "
                    + "WHERE phone_hash = :phoneHash AND status = 'ACTIVE' AND deleted_at IS NULL ORDER BY account_type, user_id";

    static final String FIND_BY_LOGIN_ID_SQL =
            "SELECT user_id, login_id, name, account_type, phone_hash FROM app_user "
                    + "WHERE login_id = :loginId AND status = 'ACTIVE' AND deleted_at IS NULL";

    /** 비밀번호를 바꾸고 로그인 연속 실패 횟수와 잠금도 푼다 (비밀번호를 잊어서 여러 번 틀려 잠긴 경우가 흔하다) */
    static final String RESET_PASSWORD_SQL =
            "UPDATE app_user SET password_hash = :hash, failed_login_count = 0, locked_until = NULL, updated_at = now() "
                    + "WHERE user_id = :id AND status = 'ACTIVE' AND deleted_at IS NULL";

    /** 이 계정의 모든 로그인 세션을 끝낸다 (이전 비밀번호로 만들어진 세션이 남지 않게). 이미 끝난 세션은 건드리지 않는다 */
    static final String REVOKE_SESSIONS_SQL =
            "UPDATE auth_token SET revoked_at = now(), revoke_reason = 'PASSWORD_CHANGE' WHERE user_id = :id AND revoked_at IS NULL";

    /** 세션이 모두 끝났으니 알림을 받을 기기 등록도 지운다 (다시 로그인하면 앱이 새로 등록한다) */
    static final String DELETE_PUSH_TOKENS_SQL = "DELETE FROM push_token WHERE user_id = :id";

    private final NamedParameterJdbcTemplate jdbc;

    /** 이 번호로 가입한 활성 계정들 (복약자 계정과 보호자 계정이 하나씩, 최대 2개) */
    public List<Account> findActiveByPhoneHash(String phoneHash) {
        return jdbc.query(FIND_BY_PHONE_SQL, new MapSqlParameterSource("phoneHash", phoneHash), (rs, n) -> toAccount(rs));
    }

    public Optional<Account> findActiveByLoginId(String loginId) {
        return jdbc.query(FIND_BY_LOGIN_ID_SQL, new MapSqlParameterSource("loginId", loginId), (rs, n) -> toAccount(rs))
                .stream().findFirst();
    }

    /** @return 바뀌었으면 true (그 사이 탈퇴했으면 false) */
    public boolean resetPassword(long userId, String passwordHash) {
        return jdbc.update(RESET_PASSWORD_SQL, new MapSqlParameterSource().addValue("id", userId).addValue("hash", passwordHash)) > 0;
    }

    public int revokeAllSessions(long userId) {
        return jdbc.update(REVOKE_SESSIONS_SQL, new MapSqlParameterSource("id", userId));
    }

    public int deletePushTokens(long userId) {
        return jdbc.update(DELETE_PUSH_TOKENS_SQL, new MapSqlParameterSource("id", userId));
    }

    private static Account toAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Account(rs.getLong("user_id"), rs.getString("login_id"), rs.getString("name"),
                rs.getString("account_type"), rs.getString("phone_hash"));
    }
}