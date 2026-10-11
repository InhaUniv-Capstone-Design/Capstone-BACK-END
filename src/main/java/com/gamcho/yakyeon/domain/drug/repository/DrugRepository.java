package com.gamcho.yakyeon.domain.drug.repository;

import com.gamcho.yakyeon.domain.drug.entity.Drug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 의약품 마스터 조회 (읽기 전용 데이터).
 */
public interface DrugRepository extends JpaRepository<Drug, Long> {

    /** 품목기준코드(item_seq)로 정확히 조회. 처방 등록에서 약을 확정할 때 사용 */
    Optional<Drug> findByItemSeq(String itemSeq);

    /**
     * 제품명 부분 일치 검색 (자동완성).
     *
     * ILIKE를 쓰는 이유: drug.product_name에 pg_trgm GIN 인덱스(idx_drug_name_trgm)가 있어서
     * 'ILIKE %검색어%'가 그 인덱스를 탄다. 대소문자만 맞추려고 lower()를 씌우면 인덱스를 못 타서 전체 스캔이 된다.
     * (트라이그램은 3글자 단위라 검색어가 3글자 미만이면 인덱스 효과가 없다.)
     *
     * 정렬: 검색어로 "시작하는" 제품을 먼저, 그다음 이름이 짧은 것(검색어에 더 가까운 것), 이름순,
     * 마지막으로 drug_id. 같은 이름의 제품이 업체별로 여러 개 있어서, drug_id까지 정렬 기준에 넣어야
     * 호출할 때마다 같은 순서로 나온다 (없으면 DB가 임의의 순서로 돌려줄 수 있음).
     *
     * 호출하는 쪽이 LIKE 특수문자(% _ \)를 이스케이프해서 넘겨야 한다 - 안 하면 사용자가 입력한 %가
     * 와일드카드로 동작해서 모든 약이 검색된다.
     */
    @Query(value = """
            SELECT * FROM drug
            WHERE product_name ILIKE :pattern
            ORDER BY (product_name ILIKE :prefixPattern) DESC, char_length(product_name), product_name, drug_id
            LIMIT :limit
            """, nativeQuery = true)
    List<Drug> searchByName(@Param("pattern") String pattern,
                            @Param("prefixPattern") String prefixPattern,
                            @Param("limit") int limit);
}