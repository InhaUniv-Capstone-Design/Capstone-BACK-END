package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.TermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * FR-AUTH-013
 * (기존 ConsentTextVersionRepository → TermsVersion 엔티티 이름 변경에 맞춰 리네임)
 */
public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {

    /**
     * 특정 약관 종류의 최신 버전 문구 조회 (동의 화면에 표시할 때 사용).
     * version은 "1.0" 같은 자유 문자열이라 문자열 정렬로는 최신을 보장할 수 없어서,
     * 시행 시각(effective_at) 기준 최신 행을 가져온다.
     */
    Optional<TermsVersion> findTopByTermsTypeOrderByEffectiveAtDesc(TermsVersion.TermsType termsType);

    /**
     * 특정 시점(asOf) 기준으로 "이미 시행된" 버전 중 가장 최신 버전을 조회.
     * 회원가입 시점에 아직 시행 전(effective_at이 미래)인 버전에는 동의를 요구하지 않기 위해
     * 단순 최신순이 아니라 시행 시각 조건을 함께 건다.
     */
    Optional<TermsVersion> findTopByTermsTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            TermsVersion.TermsType termsType, LocalDateTime asOf);
}