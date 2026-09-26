package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 앱 계정 — FR-AUTH-001~007
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", length = 30, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    /** SELF, GUARDIAN */
    @Column(name = "account_type", length = 10, nullable = false)
    private String accountType;

    /** ACTIVE, DELETED */
    @Column(name = "status", length = 10, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private Short failedLoginCount = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** DB의 DEFAULT now()가 채우므로 애플리케이션에서는 삽입·수정하지 않는다 */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}