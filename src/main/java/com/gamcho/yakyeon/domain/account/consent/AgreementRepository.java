package com.gamcho.yakyeon.domain.account.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * 약관 동의(user_agreement)와 동의 이력(agreement_log)을 읽고 쓴다.
 *
 * 시각은 DB의 now()를 쓴다. 서버와 DB 시계가 조금 달라도 DB의 "철회 시각 >= 동의 시각" 제약에 걸리지 않게 하려는 것이다.
 * 동의 이력(agreement_log)은 DB가 자동으로 남기지 않아서(수정·삭제만 막는다) 여기서 직접 남긴다.
 */
@Repository
@RequiredArgsConstructor
public class AgreementRepository implements ActiveConsentLookup {

    private final NamedParameterJdbcTemplate jdbc;

    /** 약관 한 버전의 종류와 이름 */
    public record TermsRef(long termsVersionId, String termsType, String version) {
    }

    /** 이 사용자의 동의 행 전부 (약관 종류·버전 이름 포함) */
    public List<ConsentRules.Row> findRows(long userId) {
        return jdbc.query("SELECT t.terms_type, ua.terms_version_id, t.version, ua.agreed, ua.agreed_at, ua.withdrawn_at "
                        + "FROM user_agreement ua JOIN terms_version t ON t.terms_version_id = ua.terms_version_id "
                        + "WHERE ua.user_id = :userId",
                new MapSqlParameterSource("userId", userId),
                (rs, n) -> new ConsentRules.Row(
                        rs.getString("terms_type"), rs.getLong("terms_version_id"), rs.getString("version"),
                        rs.getBoolean("agreed"),
                        rs.getObject("agreed_at", OffsetDateTime.class),
                        rs.getObject("withdrawn_at", OffsetDateTime.class)));
    }

    public Optional<TermsRef> findTerms(long termsVersionId) {
        List<TermsRef> found = jdbc.query(
                "SELECT terms_version_id, terms_type, version FROM terms_version WHERE terms_version_id = :id",
                new MapSqlParameterSource("id", termsVersionId),
                (rs, n) -> new TermsRef(rs.getLong("terms_version_id"), rs.getString("terms_type"), rs.getString("version")));
        return found.stream().findFirst();
    }

    /** 이 종류에서 지금 시행 중인 최신 버전 번호 (GET /terms와 같은 기준) */
    public Optional<Long> latestEffectiveVersionId(String termsType) {
        List<Long> found = jdbc.query(
                "SELECT terms_version_id FROM terms_version WHERE terms_type = :type AND effective_at <= now() "
                        + "ORDER BY effective_at DESC, terms_version_id DESC LIMIT 1",
                new MapSqlParameterSource("type", termsType),
                (rs, n) -> rs.getLong(1));
        return found.stream().findFirst();
    }

    /**
     * 동의를 남긴다. 이미 유효하게 동의한 상태면 아무것도 바꾸지 않는다(같은 요청을 다시 보내도 안전).
     * 처음 동의하거나 철회했다가 다시 동의하면 동의 시각을 지금으로 하고 철회 표시를 지운다.
     *
     * @return 동의가 새로 생겼거나 다시 살아났으면 true
     */
    public boolean agree(long userId, long termsVersionId) {
        List<Long> changed = jdbc.query(
                "INSERT INTO user_agreement (user_id, terms_version_id, agreed, agreed_at) "
                        + "VALUES (:userId, :termsVersionId, TRUE, now()) "
                        + "ON CONFLICT (user_id, terms_version_id) DO UPDATE "
                        + "SET agreed = TRUE, agreed_at = now(), withdrawn_at = NULL "
                        + "WHERE user_agreement.withdrawn_at IS NOT NULL OR NOT user_agreement.agreed "
                        + "RETURNING terms_version_id",
                new MapSqlParameterSource().addValue("userId", userId).addValue("termsVersionId", termsVersionId),
                (rs, n) -> rs.getLong(1));
        if (changed.isEmpty()) {
            return false;
        }
        insertLog(userId, termsVersionId, "AGREE");
        return true;
    }

    /**
     * 이 종류의 유효한 동의를 모두 철회한다(버전이 여럿이어도 전부). 이미 철회했으면 아무것도 하지 않는다.
     * 철회 시각은 동의 시각보다 앞서면 DB 제약에 걸리므로 둘 중 늦은 쪽으로 한다.
     *
     * @return 철회한 동의 수
     */
    public int withdrawAllActive(long userId, String termsType) {
        List<Long> withdrawn = jdbc.query(
                "UPDATE user_agreement ua SET withdrawn_at = GREATEST(now(), ua.agreed_at) "
                        + "FROM terms_version t "
                        + "WHERE t.terms_version_id = ua.terms_version_id AND ua.user_id = :userId AND t.terms_type = :termsType "
                        + "AND ua.agreed AND ua.withdrawn_at IS NULL "
                        + "RETURNING ua.terms_version_id",
                new MapSqlParameterSource().addValue("userId", userId).addValue("termsType", termsType),
                (rs, n) -> rs.getLong(1));
        for (Long termsVersionId : withdrawn) {
            insertLog(userId, termsVersionId, "WITHDRAW");
        }
        return withdrawn.size();
    }

    /** 회원가입 때 동의한 약관의 이력을 남긴다 (동의 행 자체는 가입 코드가 저장한다) */
    public void logAgree(long userId, Collection<Long> termsVersionIds) {
        for (Long termsVersionId : termsVersionIds) {
            insertLog(userId, termsVersionId, "AGREE");
        }
    }

    private void insertLog(long userId, long termsVersionId, String action) {
        jdbc.update("INSERT INTO agreement_log (user_id, terms_version_id, action) VALUES (:userId, :termsVersionId, :action)",
                new MapSqlParameterSource().addValue("userId", userId)
                        .addValue("termsVersionId", termsVersionId).addValue("action", action));
    }

    @Override
    public boolean hasAllActive(long userId, Collection<String> termsTypes) {
        int needed = new HashSet<>(termsTypes).size();
        Integer have = jdbc.queryForObject(
                "SELECT count(DISTINCT t.terms_type) FROM user_agreement ua "
                        + "JOIN terms_version t ON t.terms_version_id = ua.terms_version_id "
                        + "WHERE ua.user_id = :userId AND ua.agreed AND ua.withdrawn_at IS NULL AND t.terms_type IN (:types)",
                new MapSqlParameterSource().addValue("userId", userId).addValue("types", termsTypes),
                Integer.class);
        return have != null && have == needed;
    }
}