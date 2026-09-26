package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 문자 인증 — FR-AUTH-009·011·018
 */
@Entity
@Table(name = "phone_verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    /** DELEGATION, LEGAL_REP, REVOKE */
    @Column(name = "purpose", length = 20, nullable = false)
    private String purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", length = 64, nullable = false)
    private String phoneHash;

    /** 인증번호 원문이 아닌 해시만 저장 */
    @Column(name = "code_hash", length = 100, nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Short attemptCount = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}