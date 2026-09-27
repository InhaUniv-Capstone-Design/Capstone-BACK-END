package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.ConsentTextVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * FR-AUTH-013
 */
public interface ConsentTextVersionRepository extends JpaRepository<ConsentTextVersion, Long> {

    /** 특정 동의 유형의 최신 버전 문구 조회 (동의 화면에 표시할 때 사용) */
    Optional<ConsentTextVersion> findTopByConsentTypeOrderByVersionNoDesc(String consentType);
}