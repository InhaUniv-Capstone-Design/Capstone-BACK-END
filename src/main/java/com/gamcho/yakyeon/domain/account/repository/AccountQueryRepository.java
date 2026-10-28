package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.dto.GuardianLinkItemResponse;
import com.gamcho.yakyeon.domain.account.dto.ManagedPatientResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 설정 화면용 계정 조회·수정 SQL (연동된 보호자 목록, 내가 볼 수 있는 복약자 목록, 프로필). */
@Repository
public class AccountQueryRepository {

    /** 프로필 원본 행. 휴대폰은 암호문이라 서비스가 복호화해서 가린 뒤에만 내려준다 */
    public record ProfileRow(long userId, String loginId, String accountType, String name,
                             java.time.LocalDate birthDate, byte[] phoneEnc) {
    }

    /** 보호자 이름 마스킹 전의 행 */
    public record GuardianRow(long linkId, String guardianName, String guardianLoginId, String status,
                              String permissionScope, String consentSubject, OffsetDateTime consentedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AccountQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 내 계정(user_id)에 연결된, 삭제되지 않은 복약자 본인 프로필인지 */
    public boolean isOwnActivePatient(long userId, long patientId) {
        Boolean ok = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM patient WHERE patient_id = :pid AND user_id = :me "
                        + "AND status = 'ACTIVE')",
                new MapSqlParameterSource().addValue("pid", patientId).addValue("me", userId), Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    public List<GuardianRow> findGuardianLinks(long patientId, Collection<String> statuses) {
        return jdbc.query("SELECT g.link_id, u.name, u.login_id, g.status, g.permission_scope, g.consent_subject, g.consented_at "
                        + "FROM guardian_link g JOIN app_user u ON u.user_id = g.guardian_user_id "
                        + "WHERE g.patient_id = :pid AND g.status IN (:statuses) AND u.status = 'ACTIVE' "
                        + "ORDER BY g.created_at DESC, g.link_id DESC",
                new MapSqlParameterSource().addValue("pid", patientId).addValue("statuses", statuses),
                (rs, n) -> new GuardianRow(rs.getLong("link_id"), rs.getString("name"), rs.getString("login_id"),
                        rs.getString("status"), rs.getString("permission_scope"), rs.getString("consent_subject"),
                        rs.getObject("consented_at", OffsetDateTime.class)));
    }

    /** 본인 프로필(있으면 맨 앞) + 내가 ACTIVE 연동으로 보호하는 복약자들 */
    public List<ManagedPatientResponse> findManagedPatients(long userId) {
        return jdbc.query("SELECT patient_id, name, birth_date, has_account, my_role, link_id, permission_scope, linked_at FROM ("
                        + "SELECT p.patient_id, p.name, p.birth_date, (p.user_id IS NOT NULL) AS has_account, 'SELF' AS my_role, "
                        + "CAST(NULL AS BIGINT) AS link_id, CAST(NULL AS VARCHAR) AS permission_scope, "
                        + "CAST(NULL AS TIMESTAMPTZ) AS linked_at, 0 AS ord, p.created_at AS sort_at "
                        + "FROM patient p WHERE p.user_id = :me AND p.status = 'ACTIVE' "
                        + "UNION ALL "
                        + "SELECT p.patient_id, p.name, p.birth_date, (p.user_id IS NOT NULL), 'GUARDIAN', g.link_id, "
                        + "g.permission_scope, g.consented_at, 1, coalesce(g.consented_at, g.created_at) "
                        + "FROM guardian_link g JOIN patient p ON p.patient_id = g.patient_id "
                        + "WHERE g.guardian_user_id = :me AND g.status = 'ACTIVE' AND p.status = 'ACTIVE') x "
                        + "ORDER BY ord, sort_at, patient_id",
                new MapSqlParameterSource("me", userId), (rs, n) -> toManaged(rs));
    }

    private static ManagedPatientResponse toManaged(ResultSet rs) throws SQLException {
        OffsetDateTime linkedAt = rs.getObject("linked_at", OffsetDateTime.class);
        return new ManagedPatientResponse(rs.getLong("patient_id"), rs.getString("name"),
                rs.getObject("birth_date", java.time.LocalDate.class), rs.getBoolean("has_account"), rs.getString("my_role"),
                (Long) rs.getObject("link_id"), rs.getString("permission_scope"),
                linkedAt == null ? null : linkedAt.atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime());
    }

    public Optional<ProfileRow> findProfile(long userId) {
        return jdbc.query("SELECT user_id, login_id, account_type, name, birth_date, phone_enc FROM app_user "
                                + "WHERE user_id = :me AND status = 'ACTIVE'",
                        new MapSqlParameterSource("me", userId),
                        (rs, n) -> new ProfileRow(rs.getLong("user_id"), rs.getString("login_id"), rs.getString("account_type"),
                                rs.getString("name"), rs.getObject("birth_date", java.time.LocalDate.class), rs.getBytes("phone_enc")))
                .stream().findFirst();
    }

    /** 이름을 바꾼다. 본인(PATIENT) 계정은 복약자 프로필 이름도 함께 바꿔서 두 곳의 이름이 어긋나지 않게 한다 */
    public int updateName(long userId, String name) {
        int updated = jdbc.update("UPDATE app_user SET name = :name, updated_at = now() WHERE user_id = :me AND status = 'ACTIVE'",
                new MapSqlParameterSource().addValue("name", name).addValue("me", userId));
        if (updated > 0) {
            jdbc.update("UPDATE patient SET name = :name WHERE user_id = :me AND status = 'ACTIVE'",
                    new MapSqlParameterSource().addValue("name", name).addValue("me", userId));
        }
        return updated;
    }
}