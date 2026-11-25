package com.gamcho.yakyeon.domain.account.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 연동(guardian_link)에 복약자가 허용해 둔 열람 항목(shared_items)을 읽는다. 접근 가드가 보호자 요청마다 부른다.
 * 항목은 PostgreSQL 배열이라 쉼표로 이은 문자열로 읽고, 해석은 SharedItemRules.parse가 한다.
 */
@Repository
@RequiredArgsConstructor
public class SharedItemsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** 허용한 항목이 없거나 연동이 없으면 빈 문자열 */
    public String findSharedItemsText(long linkId) {
        List<String> rows = jdbc.query(
                "SELECT COALESCE(array_to_string(shared_items, ','), '') FROM guardian_link WHERE link_id = :linkId",
                new MapSqlParameterSource("linkId", linkId), (rs, n) -> rs.getString(1));
        return rows.isEmpty() ? "" : rows.get(0);
    }
}