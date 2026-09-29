package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refresh Token 저장용 [복원]
 * Access Token은 무상태 JWT로 발급하고, 여기서는 Refresh Token만 관리해서
 * 강제 로그아웃 / 비밀번호 변경 시 전체 무효화 / 재사용(탈취) 탐지를 가능하게 함.
 *
 * 보안 주의: tokenHash는 반드시 토큰 원문을 SHA-256 등으로 해시한 값이어야 함.
 *           원문 토큰을 이 필드에 저장하지 말 것 (DB 유출 시 그대로 재사용 가능해짐).
 */
@Entity
@Table(name = "auth_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "device_label", length = 50)
    private String deviceLabel;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuthToken(AppUser user, String tokenHash, String deviceLabel, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.deviceLabel = deviceLabel;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isValid() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    /** 로그아웃 / 재사용 탐지 시 호출 */
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}