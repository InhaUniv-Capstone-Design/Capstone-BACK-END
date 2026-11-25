package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Refresh Token 저장용 (auth_token)
 * Access Token은 무상태 JWT로 발급하고, 여기서는 Refresh Token만 관리해서
 * 강제 로그아웃 / 비밀번호 변경 시 전체 무효화 / 재사용(탈취) 탐지를 가능하게 함.
 *
 * 보안 주의: tokenHash는 반드시 토큰 원문을 SHA-256으로 해시한 값(64자 hex)이어야 함.
 *           원문 토큰을 이 필드에 저장하지 말 것 (DB 유출 시 그대로 재사용 가능해짐).
 *
 * DB 제약(팀 스키마): revoked_at과 revoke_reason은 "둘 다 채워지거나 둘 다 비어 있어야" 한다.
 * 그래서 폐기는 항상 사유와 함께 revoke(reason)으로만 한다.
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

    /** DB 컬럼이 CHAR(64)라서 JDBC 타입을 CHAR로 명시해야 스키마 검증을 통과한다 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "device_label", length = 100)
    private String deviceLabel;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 20)
    private RevokeReason revokeReason;

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

    /** 토큰을 사용(재발급)할 때 마지막 사용 시각 갱신 */
    public void markUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * 폐기. 이미 폐기된 토큰이면 처음 기록된 시각과 사유를 그대로 둔다
     * (나중에 "왜 폐기됐는지"가 덮어써지면 재사용 탐지 판단이 틀어진다).
     */
    public void revoke(RevokeReason reason) {
        if (this.revokedAt != null) {
            return;
        }
        this.revokedAt = LocalDateTime.now();
        this.revokeReason = reason;
    }

    /** DB CHECK 제약(ck_auth_token_reason)의 허용값과 정확히 같아야 한다 */
    public enum RevokeReason {
        LOGOUT,           // 로그아웃
        ROTATED,          // 재발급으로 새 토큰으로 교체됨
        PASSWORD_CHANGE,  // 비밀번호 변경
        WITHDRAWAL,       // 탈퇴
        SECURITY          // 재사용 탐지 등 보안 사유 강제 폐기
    }
}