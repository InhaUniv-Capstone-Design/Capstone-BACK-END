package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보호자 연동 (guardian_link) — 이전 care_link
 * FR-AUTH-009~015, FR-MULTI-001, 006, FR-SERVER-006
 */
@Entity
@Table(name = "guardian_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long linkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id", nullable = false)
    private AppUser guardianUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private LinkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_scope", nullable = false, length = 10)
    private PermissionScope permissionScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_subject", length = 10)
    private ConsentSubject consentSubject;

    @Column(name = "consented_at")
    private LocalDateTime consentedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public GuardianLink(AppUser guardianUser, Patient patient, PermissionScope permissionScope) {
        this.guardianUser = guardianUser;
        this.patient = patient;
        this.status = LinkStatus.PENDING;
        // DB 기본값(READ_ONLY, 최소 권한)과 맞춘다. 값을 넘기지 않으면 읽기 전용으로 시작한다
        this.permissionScope = permissionScope != null ? permissionScope : PermissionScope.READ_ONLY;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** 복약자(또는 법정대리인)의 동의 처리 */
    public void activate(ConsentSubject subject) {
        this.status = LinkStatus.ACTIVE;
        this.consentSubject = subject;
        this.consentedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = LinkStatus.REJECTED;
    }

    /**
     * 권한 범위 변경. "누가 올릴 수 있는가" 같은 규칙은 여기서 판단하지 않는다 -
     * 호출하는 서비스(GuardianLinkService)가 호출자를 확인한 뒤에만 부른다.
     */
    public void changePermissionScope(PermissionScope newScope) {
        this.permissionScope = newScope;
    }

    public void revoke() {
        this.status = LinkStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
    }

    /**
     * 보호자 접근 통제 - 요청한 권한 범위가 현재 연동 상태에서 허용되는지 확인.
     * 서버 로직이 책임져야 하는 규칙(DB 제약만으로 못 막음)을 여기 캡슐화.
     */
    public boolean permits(PermissionScope required) {
        if (status != LinkStatus.ACTIVE) {
            return false;
        }
        return permissionScope == PermissionScope.READ_WRITE || required == PermissionScope.READ_ONLY;
    }

    public enum LinkStatus {
        PENDING, ACTIVE, REJECTED, REVOKED
    }

    public enum PermissionScope {
        READ_ONLY, READ_WRITE
    }

    public enum ConsentSubject {
        SELF, LEGAL_REP
    }
}