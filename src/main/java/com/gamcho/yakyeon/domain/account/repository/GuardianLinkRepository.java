package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * FR-AUTH-012~015, FR-MULTI-*
 * (기존 CareLinkRepository → GuardianLink 엔티티 이름 변경에 맞춰 리네임)
 */
public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {

    /**
     * 보호자-복약자 쌍의 "진행 중" 연동 조회 (PENDING·ACTIVE).
     * DB의 uq_guardian_link_open 부분 유니크 인덱스와 같은 조건이므로,
     * 새 연동 요청 생성 전 이 메서드로 먼저 중복 여부를 확인한다.
     */
    Optional<GuardianLink> findByGuardianUser_UserIdAndPatient_PatientIdAndStatusIn(
            Long guardianUserId, Long patientId, List<GuardianLink.LinkStatus> statuses);

    /** FR-MY-008: 복약자 본인이 자신에게 연동된 보호자 목록 조회 */
    List<GuardianLink> findByPatient_PatientId(Long patientId);

    /** FR-MULTI-002: 보호자가 관리 중인 복약자 목록 조회 */
    List<GuardianLink> findByGuardianUser_UserIdAndStatus(Long guardianUserId, GuardianLink.LinkStatus status);
}