package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 보호자–복약자 연동 — FR-AUTH-012~015, FR-MULTI-*
 */
@Entity
@Table(name = "care_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "care_link_id")
    private Long careLinkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id", nullable = false)
    private AppUser guardianUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** DELEGATE, LEGAL_REPRESENTATIVE */
    @Column(name = "link_type", length = 20, nullable = false)
    private String linkType;

    /** VIEW, EDIT */
    @Column(name = "permission", length = 10, nullable = false)
    @Builder.Default
    private String permission = "VIEW";

    /** PENDING, ACTIVE, REJECTED, REVOKED */
    @Column(name = "status", length = 10, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /** 연동 근거(GRANT 이력). PENDING이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_log_id")
    private ConsentLog consentLog;

    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    /** 해제 시 즉시 접근 차단 */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}