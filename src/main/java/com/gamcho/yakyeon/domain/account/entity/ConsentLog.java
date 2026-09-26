package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 동의 이력 — FR-AUTH-008·013·014, NFR-SEC-003
 *
 * 추가 전용(append-only) 테이블이다. DB 트리거(trg_consent_log_append_only)가
 * UPDATE/DELETE를 막고 있으므로, 서비스 코드에서도 이 엔티티는
 * save(insert)만 사용하고 별도의 update 로직을 만들지 않는다.
 */
@Entity
@Table(name = "consent_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_log_id")
    private Long consentLogId;

    /** 약관·개인정보 동의 주체 계정 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** 민감정보·위임 동의의 대상 복약자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    /** 위임받는 보호자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id")
    private AppUser guardianUser;

    /** TERMS, PRIVACY, SENSITIVE_HEALTH, DELEGATION, LEGAL_REP */
    @Column(name = "consent_type", length = 20, nullable = false)
    private String consentType;

    /** GRANT, REVOKE (철회는 새 행으로 기록) */
    @Column(name = "action", length = 10, nullable = false)
    private String action;

    /** SELF, LEGAL_REPRESENTATIVE */
    @Column(name = "actor_type", length = 20, nullable = false)
    private String actorType;

    /** VIEW, EDIT (nullable) */
    @Column(name = "scope", length = 10)
    private String scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_version_id", nullable = false)
    private ConsentTextVersion textVersion;

    /** 본인 인증 근거 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id")
    private PhoneVerification verification;

    /** 변조 탐지용(선택) */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "row_hash", length=64)
    private String rowHash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}