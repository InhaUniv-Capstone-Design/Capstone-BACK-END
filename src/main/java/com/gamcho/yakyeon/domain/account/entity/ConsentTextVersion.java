package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 동의 문구 버전 — FR-AUTH-013
 */
@Entity
@Table(name = "consent_text_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentTextVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "text_version_id")
    private Long textVersionId;

    /** TERMS, PRIVACY, SENSITIVE_HEALTH, DELEGATION, LEGAL_REP */
    @Column(name = "consent_type", length = 20, nullable = false)
    private String consentType;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}