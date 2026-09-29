package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 약관 버전 (terms_version)
 * FR-AUTH-008, 013, 016, 017
 */
@Entity
@Table(name = "terms_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_version_id")
    private Long termsVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType termsType;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /**
     * DB 컬럼은 TEXT(가변 길이 문자열, VARCHAR 계열)다.
     * @Lob을 쓰면 Hibernate가 CLOB으로 매핑해서 PostgreSQL에서는 oid(대용량 객체) 타입을
     * 기대하게 되는데 실제 컬럼은 TEXT라 스키마 검증에서 어긋난다.
     * 그냥 일반 String 필드로 매핑하면 된다 (길이 제한만 안 거는 것).
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Builder
    public TermsVersion(TermsType termsType, String version, String content, Boolean isRequired, LocalDateTime effectiveAt) {
        this.termsType = termsType;
        this.version = version;
        this.content = content;
        this.isRequired = isRequired != null ? isRequired : Boolean.TRUE;
        this.effectiveAt = effectiveAt;
    }

    public enum TermsType {
        TOS, PRIVACY, SENSITIVE_HEALTH, SERVICE_NOTICE, DELEGATION, LEGAL_REP
    }
}