package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 약관 동의 내역 (user_agreement) [신규]
 * FR-AUTH-008, 016, 017
 */
@Entity
@Table(name = "user_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreement {

    @EmbeddedId
    private UserAgreementId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @MapsId("termsVersionId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_version_id")
    private TermsVersion termsVersion;

    @Column(name = "agreed", nullable = false)
    private Boolean agreed;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    @Builder
    public UserAgreement(AppUser user, TermsVersion termsVersion, Boolean agreed) {
        this.user = user;
        this.termsVersion = termsVersion;
        this.id = new UserAgreementId(user.getUserId(), termsVersion.getTermsVersionId());
        this.agreed = agreed;
    }

    @PrePersist
    protected void onCreate() {
        this.agreedAt = LocalDateTime.now();
    }
}