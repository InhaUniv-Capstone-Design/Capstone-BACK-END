package com.gamcho.yakyeon.domain.interaction.repository;

import com.gamcho.yakyeon.domain.interaction.logic.FoundPair;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DUR(병용금기) 데이터 조회. 데이터가 79만 건이 넘는 읽기 전용 참고 테이블이라 엔티티 없이 SQL로 바로 읽는다.
 * 모든 값은 이름 붙은 파라미터로 바인딩한다 (문자열을 SQL에 이어 붙이지 않는다).
 */
@Repository
public class DurTabooLookup {

    private final NamedParameterJdbcTemplate jdbc;

    public DurTabooLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 가장 최근에 성공한 DUR 데이터 회차 (기준일이 같으면 나중에 적재한 것) */
    public Optional<DatasetInfo> latestSuccessfulDataset() {
        String sql = "SELECT dur_dataset_id, base_date FROM dur_dataset "
                + "WHERE status = 'SUCCESS' ORDER BY base_date DESC, dur_dataset_id DESC LIMIT 1";
        return jdbc.query(sql, Collections.<String, Object>emptyMap(),
                        (rs, n) -> new DatasetInfo(rs.getLong("dur_dataset_id"), rs.getObject("base_date", LocalDate.class)))
                .stream().findFirst();
    }

    /**
     * 주어진 품목코드들 "사이"의 병용금기 제품 쌍 (두 품목이 모두 이 목록 안에 있는 쌍만).
     * 기본키(품목A, 품목B, DUR번호)와 품목B 인덱스를 타서 약 수십 개여도 몇 ms 안에 끝난다.
     */
    public List<FoundPair> findPairs(Collection<String> itemSeqs) {
        if (itemSeqs.size() < 2) {
            return List.of();
        }
        String sql = "SELECT i.item_seq_a, i.item_seq_b, i.dur_seq, i.ingredient_code_a, i.ingredient_code_b, "
                + "       r.type_code, r.prohbt_content, r.remark, r.source_name "
                + "  FROM dur_taboo_item i "
                + "  JOIN dur_taboo_rule r ON r.dur_seq = i.dur_seq "
                + "   AND r.ingredient_a_code = i.rule_ingr_lo AND r.ingredient_b_code = i.rule_ingr_hi "
                + " WHERE i.item_seq_a IN (:seqs) AND i.item_seq_b IN (:seqs)";
        return jdbc.query(sql, Map.of("seqs", itemSeqs),
                (rs, n) -> new FoundPair(
                        rs.getString("item_seq_a"), rs.getString("item_seq_b"), rs.getString("dur_seq"),
                        rs.getString("ingredient_code_a"), rs.getString("ingredient_code_b"),
                        rs.getString("type_code"), rs.getString("prohbt_content"),
                        rs.getString("remark"), rs.getString("source_name")));
    }

    /** DUR 번호 하나의 규칙 상세 (엣지 클릭 팝업용). 이 데이터에서는 DUR 번호와 규칙이 일대일이다 */
    public Optional<RuleDetail> findRule(String durSeq) {
        String sql = "SELECT r.prohbt_content, r.remark, r.source_name, d.base_date "
                + "  FROM dur_taboo_rule r JOIN dur_dataset d ON d.dur_dataset_id = r.dur_dataset_id "
                + " WHERE r.dur_seq = :durSeq ORDER BY d.base_date DESC LIMIT 1";
        return jdbc.query(sql, Map.of("durSeq", durSeq),
                        (rs, n) -> new RuleDetail(rs.getString("prohbt_content"), rs.getString("remark"),
                                rs.getString("source_name"), rs.getObject("base_date", LocalDate.class)))
                .stream().findFirst();
    }

    public record DatasetInfo(long datasetId, LocalDate baseDate) {
    }

    public record RuleDetail(String reason, String remark, String sourceName, LocalDate baseDate) {
    }
}