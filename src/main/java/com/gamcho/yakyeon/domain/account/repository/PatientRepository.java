package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * FR-AUTH-009~018
 *
 * 팀 스키마의 patient는 탈퇴해도 행이 남는다(status = DELETED, 개인정보 파기).
 * 그래서 "활성 복약자"를 찾는 조회는 반드시 status 조건을 같이 걸어야 한다.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** 복약자 본인 계정으로 조회 */
    Optional<Patient> findByUser_UserId(Long userId);

    /**
     * 전화번호 해시로 활성 복약자 조회.
     * phone_hash에는 유니크 제약이 없다 - 보호자 두 명이 같은 부모님을 각자 따로 등록하면
     * 같은 번호의 복약자가 여러 행 생긴다. 그래서 Optional이 아니라 List로 받는다.
     */
    List<Patient> findAllByPhoneHashAndStatus(String phoneHash, Patient.PatientStatus status);

    /** ID로 조회하되 상태까지 확인 - 탈퇴한 복약자에게 연동 요청이 만들어지는 걸 막는다 */
    Optional<Patient> findByPatientIdAndStatus(Long patientId, Patient.PatientStatus status);
}