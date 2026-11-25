package com.gamcho.yakyeon.domain.account.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 복약자 웹 동의(열람 범위)용 DB 접근. shared_items는 PostgreSQL 배열(VARCHAR(20)[])이라 JPA 매핑 대신 SQL로 직접 다룬다.
 * 배열 값은 SharedItemRules.toArrayLiteral이 만든 문자열("{A,B}", 정해진 6개 이름만)을 CAST해서 넣는다.
 */
@Repository
@RequiredArgsConstructor
public class WebConsentRepository {

    /** 내 복약자 프로필 */
    public record PatientRef(long patientId, LocalDate birthDate) {
    }

    /** 복약자 본인이 자기 연동 하나를 잠그고 읽은 결과 */
    public record LinkRow(long linkId, long patientId, long guardianUserId, String status, String permissionScope,
                          String sharedItemsText, LocalDate patientBirthDate) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** 대리인(보호자) 계정의 user_id. 없는 아이디, 보호자 계정이 아님, 탈퇴한 계정은 모두 비어 있다(구분하지 않는다) */
    public Optional<Long> findActiveGuardianUserId(String loginId) {
        return jdbc.query("SELECT user_id FROM app_user WHERE login_id = :loginId AND account_type = 'GUARDIAN' "
                                + "AND status = 'ACTIVE' AND deleted_at IS NULL",
                        new MapSqlParameterSource("loginId", loginId), (rs, n) -> rs.getLong(1))
                .stream().findFirst();
    }

    /** 이 계정의 살아 있는 복약자 프로필 */
    public Optional<PatientRef> findOwnActivePatient(long userId) {
        return jdbc.query("SELECT patient_id, birth_date FROM patient WHERE user_id = :me AND status = 'ACTIVE'",
                        new MapSqlParameterSource("me", userId),
                        (rs, n) -> new PatientRef(rs.getLong("patient_id"), rs.getObject("birth_date", LocalDate.class)))
                .stream().findFirst();
    }

    /** 같은 보호자와 복약자 사이에 살아 있는(PENDING, ACTIVE) 연동이 있는지 */
    public boolean liveLinkExists(long guardianUserId, long patientId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM guardian_link WHERE guardian_user_id = :g AND patient_id = :p "
                        + "AND status IN ('PENDING', 'ACTIVE'))",
                new MapSqlParameterSource().addValue("g", guardianUserId).addValue("p", patientId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /** 필수 3개 동의: 활성 연동을 만든다. 권한은 쓰기까지(READ_WRITE)로 시작하고 복약자가 나중에 낮출 수 있다 */
    public long insertActiveLink(long guardianUserId, long patientId, String relationship, String itemsLiteral) {
        Long linkId = jdbc.queryForObject(
                "INSERT INTO guardian_link (guardian_user_id, patient_id, status, permission_scope, consent_subject, "
                        + "consented_at, shared_items, relationship) "
                        + "VALUES (:guardianUserId, :patientId, 'ACTIVE', 'READ_WRITE', 'SELF', now(), "
                        + "CAST(:items AS VARCHAR(20)[]), CAST(:relationship AS VARCHAR(20))) RETURNING link_id",
                new MapSqlParameterSource().addValue("guardianUserId", guardianUserId).addValue("patientId", patientId)
                        .addValue("items", itemsLiteral).addValue("relationship", relationship),
                Long.class);
        return linkId;
    }

    /** 필수 항목 비동의: 연동을 만들지 않고 "거절됨" 기록만 남긴다. 공유되는 항목은 없다 */
    public long insertRejectedLink(long guardianUserId, long patientId, String relationship) {
        Long linkId = jdbc.queryForObject(
                "INSERT INTO guardian_link (guardian_user_id, patient_id, status, permission_scope, consent_subject, "
                        + "shared_items, relationship) "
                        + "VALUES (:guardianUserId, :patientId, 'REJECTED', 'READ_ONLY', 'SELF', '{}', "
                        + "CAST(:relationship AS VARCHAR(20))) RETURNING link_id",
                new MapSqlParameterSource().addValue("guardianUserId", guardianUserId).addValue("patientId", patientId)
                        .addValue("relationship", relationship),
                Long.class);
        return linkId;
    }

    /** 동의 이력(append-only). 행동 주체는 복약자 본인(PATIENT)이다 */
    public void insertConsentLog(long patientId, long guardianUserId, long linkId, String action,
                                 String permissionScope, long termsVersionId, String itemsLiteral) {
        jdbc.update("INSERT INTO consent_log (patient_id, guardian_user_id, link_id, action, consent_subject, "
                        + "permission_scope, terms_version_id, actor_type, shared_items) "
                        + "VALUES (:patientId, :guardianUserId, :linkId, :action, 'SELF', :scope, :termsVersionId, 'PATIENT', "
                        + "CAST(:items AS VARCHAR(20)[]))",
                new MapSqlParameterSource().addValue("patientId", patientId).addValue("guardianUserId", guardianUserId)
                        .addValue("linkId", linkId).addValue("action", action).addValue("scope", permissionScope)
                        .addValue("termsVersionId", termsVersionId).addValue("items", itemsLiteral));
    }

    /** 내 복약자 프로필에 연결된 연동 하나를 잠그고 읽는다. 내 것이 아니면 비어 있다(존재 여부를 숨긴다) */
    public Optional<LinkRow> lockOwnLink(long linkId, long userId) {
        return jdbc.query("SELECT g.link_id, g.patient_id, g.guardian_user_id, g.status, g.permission_scope, "
                                + "COALESCE(array_to_string(g.shared_items, ','), '') AS items, p.birth_date "
                                + "FROM guardian_link g JOIN patient p ON p.patient_id = g.patient_id "
                                + "WHERE g.link_id = :linkId AND p.user_id = :me AND p.status = 'ACTIVE' FOR UPDATE OF g",
                        new MapSqlParameterSource().addValue("linkId", linkId).addValue("me", userId),
                        (rs, n) -> new LinkRow(rs.getLong("link_id"), rs.getLong("patient_id"), rs.getLong("guardian_user_id"),
                                rs.getString("status"), rs.getString("permission_scope"), rs.getString("items"),
                                rs.getObject("birth_date", LocalDate.class)))
                .stream().findFirst();
    }

    public void updateSharedItems(long linkId, String itemsLiteral) {
        jdbc.update("UPDATE guardian_link SET shared_items = CAST(:items AS VARCHAR(20)[]) WHERE link_id = :linkId",
                new MapSqlParameterSource().addValue("items", itemsLiteral).addValue("linkId", linkId));
    }
}