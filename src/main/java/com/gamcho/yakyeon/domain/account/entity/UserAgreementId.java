package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * user_agreement 복합키 (user_id, terms_version_id)
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreementId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "terms_version_id")
    private Long termsVersionId;

    public UserAgreementId(Long userId, Long termsVersionId) {
        this.userId = userId;
        this.termsVersionId = termsVersionId;
    }
}