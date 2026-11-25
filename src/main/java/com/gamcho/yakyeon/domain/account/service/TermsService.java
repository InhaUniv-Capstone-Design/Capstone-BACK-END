package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.domain.account.dto.TermsResponse;
import com.gamcho.yakyeon.domain.account.entity.TermsVersion;
import com.gamcho.yakyeon.domain.account.repository.TermsVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TermsService {

    private final TermsVersionRepository termsVersionRepository;

    /**
     * 약관 종류별로 "지금 시행 중인 최신 버전"을 하나씩 돌려준다.
     * 회원가입 검증(AuthService)과 같은 조회 메서드를 써서, 프론트가 받아간 버전과
     * 가입 시 서버가 최신이라고 판단하는 버전의 기준이 어긋나지 않게 한다.
     *
     * 시드가 아직 없는 종류는 에러 대신 목록에서 빠진다 - 조회용 API라서 있는 것만 돌려주고,
     * 가입 시점에 필수 약관이 비어 있는 경우의 에러(TERMS_NOT_CONFIGURED)는 AuthService가 담당한다.
     */
    @Transactional(readOnly = true)
    public List<TermsResponse> getCurrentTerms() {
        LocalDateTime now = LocalDateTime.now();
        List<TermsResponse> result = new ArrayList<>();

        for (TermsVersion.TermsType type : TermsVersion.TermsType.values()) {
            termsVersionRepository
                    .findTopByTermsTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(type, now)
                    .map(this::toResponse)
                    .ifPresent(result::add);
        }
        return result;
    }

    private TermsResponse toResponse(TermsVersion tv) {
        return new TermsResponse(
                tv.getTermsVersionId(),
                tv.getTermsType().name(),
                tv.getVersion(),
                tv.getContent(),
                tv.getIsRequired()
        );
    }
}