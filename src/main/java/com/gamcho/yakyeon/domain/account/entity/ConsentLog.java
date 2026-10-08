package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 동의 이력 (consent_log) — 추가만 가능(append-only)
 * FR-AUTH-013, FR-SERVER-007, NFR-SEC-003, FR-MY-010
 *
 * 보안/무결성 주의:
 * - DB 트리거가 UPDATE/DELETE를 원천 차단하므로, 이 엔티티는 의도적으로
 *   setter나 상태 변경 메서드를 전혀 제공하지 않는다.
 * - 정정이 필요하면 새 행(SCOPE_CHANGE 등)을 추가할 것 — 기존 행을 고치지 말 것.
 * - patient/guardian_user/link는 탈퇴 후에도 이력이 남도록 FK 없이 ID만 보관한다.
 */
@Entity
@Table(name = "consent_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_log_id")
    private Long consentLogId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "guardian_user_id", nullable = false)
    private Long guardianUserId;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 12)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_subject", nullable = false, length = 10)
    private GuardianLink.ConsentSubject consentSubject;

    @Column(name = "legal_rep_name", length = 50)
    private String legalRepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_scope", nullable = false, length = 10)
    private GuardianLink.PermissionScope permissionScope;

    @Column(name = "terms_version_id", nullable = false)
    private Long termsVersionId;

    @Column(name = "verification_id")
    private Long verificationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Builder
    public ConsentLog(Long patientId, Long guardianUserId, Long linkId, Action action,
                      GuardianLink.ConsentSubject consentSubject, String legalRepName,
                      GuardianLink.PermissionScope permissionScope, Long termsVersionId,
                      Long verificationId) {
        if (consentSubject == GuardianLink.ConsentSubject.LEGAL_REP
                && (legalRepName == null || legalRepName.isBlank())) {
            throw new IllegalArgumentException("법정대리인 동의(LEGAL_REP)는 legalRepName이 필수입니다.");
        }
        this.patientId = patientId;
        this.guardianUserId = guardianUserId;
        this.linkId = linkId;
        this.action = action;
        this.consentSubject = consentSubject;
        this.legalRepName = legalRepName;
        this.permissionScope = permissionScope;
        this.termsVersionId = termsVersionId;
        this.verificationId = verificationId;
    }

    @PrePersist
    protected void onCreate() {
        this.occurredAt = LocalDateTime.now();
    }

    public enum Action {
        GRANT, REJECT, REVOKE, SCOPE_CHANGE
    }
}