package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * FR-AUTH-009~018
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** 복약자 본인 계정으로 조회 */
    Optional<Patient> findByUser_UserId(Long userId);

    /**
     * 전화번호 해시로 복약자 조회.
     * phone_hash에는 유니크 제약이 없다 - 보호자 두 명이 같은 부모님을 각자 따로 등록하면
     * 같은 번호의 복약자가 여러 행 생긴다. 그래서 Optional이 아니라 List로 받는다
     * (Optional로 받으면 중복 행이 있을 때 조회 시점에 예외가 터진다).
     */
    List<Patient> findAllByPhoneHash(String phoneHash);
}