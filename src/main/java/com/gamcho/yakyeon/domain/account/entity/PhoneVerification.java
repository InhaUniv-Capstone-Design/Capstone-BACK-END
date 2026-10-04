package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 문자 인증 (phone_verification)
 * FR-AUTH-009, 011, 018
 *
 * 보안 주의: codeHash는 인증번호를 해시(SHA-256 등)한 값만 저장.
 *           평문 인증번호를 DB에 저장하지 말 것.
 */
@Entity
@Table(name = "phone_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification {

    /** DB CHECK 제약(attempt_count <= 10)과 별개로 서비스 계층에서도 동일 상한을 강제 */
    private static final short MAX_ATTEMPTS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    /**
     * DB 컬럼이 CHAR(64)(bpchar)라서 @JdbcTypeCode(SqlTypes.CHAR)로 JDBC 타입을 명시.
     * (patient.phone_hash와 동일한 이유 — columnDefinition만으론 스키마 검증을 통과 못 함)
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private Short attemptCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * 이 인증이 속한 보호자 연동 요청(guardian_link.link_id). FK는 없음(이력성 데이터).
     * 인증을 특정 연동 요청에 묶어서, 다른 보호자가 남의 verification_id로
     * 자기 동의를 승인받는 걸 막는다.
     */
    @Column(name = "link_id")
    private Long linkId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PhoneVerification(String phoneHash, Purpose purpose, String codeHash,
                             LocalDateTime expiresAt, Long linkId) {
        this.phoneHash = phoneHash;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.linkId = linkId;
        this.attemptCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** 만료·이미 검증됨·시도 초과가 아니면 시도 가능 */
    public boolean canAttempt() {
        return !isExpired() && !isVerified() && attemptCount < MAX_ATTEMPTS;
    }

    public void increaseAttempt() {
        this.attemptCount = (short) (this.attemptCount + 1);
    }

    public void markVerified() {
        this.verifiedAt = LocalDateTime.now();
    }

    public enum Purpose {
        DELEGATION, LEGAL_REP, REVOKE
    }
}