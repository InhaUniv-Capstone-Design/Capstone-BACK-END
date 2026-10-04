package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * FR-AUTH-009·011·018
 */
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /**
     * 인증번호 확인 시 사용 - 행에 쓰기 락을 건다.
     * 락이 없으면 같은 인증에 동시 요청을 여러 개 보내서 "시도 횟수 10회 제한"을
     * 넘겨 대입할 수 있다 (둘 다 attempt_count를 같은 값으로 읽고 통과하기 때문).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PhoneVerification v where v.verificationId = :id")
    Optional<PhoneVerification> findByIdForUpdate(@Param("id") Long id);

    /**
     * 문자 인증 철회(REVOKE)용 - 같은 번호·목적의 가장 최근 인증을 락을 걸고 조회한다.
     * 철회는 로그인 없이 번호로만 접근하므로 verification_id를 응답에 노출하지 않고,
     * 번호로 최신 인증을 찾는 방식을 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerification> findFirstByPhoneHashAndPurposeOrderByCreatedAtDesc(
            String phoneHash, PhoneVerification.Purpose purpose);

    /**
     * 같은 번호·같은 목적으로 최근 발송한 건수 - 발송 한도 확인용.
     * 목적별로 따로 센다. 목적을 안 나누면 누군가 철회 요청을 반복해서 보내는 것만으로
     * 그 번호의 위임 동의 문자까지 한도에 걸려 막힐 수 있다.
     */
    long countByPhoneHashAndPurposeAndCreatedAtAfter(
            String phoneHash, PhoneVerification.Purpose purpose, LocalDateTime since);
}