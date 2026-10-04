package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * FR-AUTH-012~015, FR-MULTI-*
 */
public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {

    /**
     * 보호자-복약자 쌍의 "진행 중" 연동 조회 (PENDING·ACTIVE).
     * DB의 uq_guardian_link_open 부분 유니크 인덱스와 같은 조건이므로,
     * 새 연동 요청 생성 전 이 메서드로 먼저 중복 여부를 확인한다.
     */
    Optional<GuardianLink> findByGuardianUser_UserIdAndPatient_PatientIdAndStatusIn(
            Long guardianUserId, Long patientId, List<GuardianLink.LinkStatus> statuses);

    /**
     * "내 연동 요청"만 조회 - 남의 연동 ID를 넣어도 존재 여부가 드러나지 않게
     * (조회 결과 없음 = 404) 보호자 ID를 조건에 함께 건다.
     */
    Optional<GuardianLink> findByLinkIdAndGuardianUser_UserId(Long linkId, Long guardianUserId);

    /**
     * 문자 인증 철회용 - 같은 번호를 가진 복약자들(patientIds)의 특정 상태 연동을 모두 조회.
     * 호출 전에 patientIds가 비어 있지 않은지 확인할 것 (빈 IN 절 방지).
     */
    List<GuardianLink> findByPatient_PatientIdInAndStatus(
            Collection<Long> patientIds, GuardianLink.LinkStatus status);

    /** FR-MY-008: 복약자 본인이 자신에게 연동된 보호자 목록 조회 */
    List<GuardianLink> findByPatient_PatientId(Long patientId);

    /** FR-MULTI-002: 보호자가 관리 중인 복약자 목록 조회 */
    List<GuardianLink> findByGuardianUser_UserIdAndStatus(Long guardianUserId, GuardianLink.LinkStatus status);
}