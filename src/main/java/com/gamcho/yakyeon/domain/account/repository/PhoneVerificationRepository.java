package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * FR-AUTH-009·011·018
 */
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /** 같은 번호·같은 목적의 가장 최근 인증 시도 조회 (인증번호 확인 시 사용) */
    Optional<PhoneVerification> findTopByPhoneHashAndPurposeOrderByCreatedAtDesc(
            String phoneHash, PhoneVerification.Purpose purpose);
}