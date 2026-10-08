package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.UserAgreement;
import com.gamcho.yakyeon.domain.account.entity.UserAgreementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * FR-AUTH-008, 016, 017
 */
public interface UserAgreementRepository extends JpaRepository<UserAgreement, UserAgreementId> {

    /** 계정의 전체 약관 동의 내역 조회 (마이페이지 등에서 사용 가능) */
    List<UserAgreement> findByUser_UserId(Long userId);
}