package com.gamcho.yakyeon.domain.drug.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.drug.dto.DrugSuggestionResponse;
import com.gamcho.yakyeon.domain.drug.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DrugService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int MAX_LIMIT = 20;

    private final DrugRepository drugRepository;

    /**
     * 약물명 자동완성.
     *  - 검색어가 2글자 미만이면 빈 목록 (타이핑 도중 한 글자씩 호출되는 자동완성 특성상 에러가 아니라 빈 결과가 자연스럽다)
     *  - 50자를 넘으면 400 (정상적인 약 이름 검색이 아니고, 긴 패턴으로 DB를 괴롭히는 걸 막기 위함)
     *  - limit은 1~20으로 보정 (기본 10)
     */
    @Transactional(readOnly = true)
    public List<DrugSuggestionResponse> autocomplete(String rawQuery, int limit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_SEARCH_QUERY);
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        String escaped = escapeLike(query);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        return drugRepository.searchByName("%" + escaped + "%", escaped + "%", safeLimit).stream()
                .map(d -> new DrugSuggestionResponse(d.getDrugId(), d.getProductName(), d.getManufacturer()))
                .toList();
    }

    /** LIKE의 특수문자(\ % _)를 문자 그대로 검색하도록 이스케이프 (PostgreSQL LIKE의 기본 이스케이프 문자는 \) */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}