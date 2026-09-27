package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * FR-AUTH-009~018
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** 복약자 본인 계정으로 조회 */
    Optional<Patient> findByUser_UserId(Long userId);

    /** 본인 인증 흐름에서 전화번호 해시로 기존 복약자 찾기 */
    Optional<Patient> findByPhoneHash(String phoneHash);
}