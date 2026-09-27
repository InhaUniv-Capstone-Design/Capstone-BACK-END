package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.ConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * FR-AUTH-008·013·014, NFR-SEC-003
 *
 * 추가 전용(append-only) 엔티티다. save()는 새 이력을 남길 때만 사용하고,
 * 이미 저장된 행을 조회해서 값을 바꾼 뒤 다시 save()하는 방식(=update)은
 * 절대 사용하지 않는다 (DB 트리거가 어차피 막지만, 서비스 코드 레벨에서도
 * 이 규칙을 지켜야 한다).
 */
public interface ConsentLogRepository extends JpaRepository<ConsentLog, Long> {

    /** FR-MY-010: 본인 동의 이력 조회 (최신순) */
    List<ConsentLog> findByPatient_PatientIdOrderByCreatedAtDesc(Long patientId);
}