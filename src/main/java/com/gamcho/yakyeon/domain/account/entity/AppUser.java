package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 앱 계정 (app_user)
 * FR-AUTH-001~007, NFR-SEC-001, 004
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, length = 30)
    private String loginId;

    /** bcrypt/argon2 등으로 해시된 값만 저장. 평문 비밀번호를 이 필드에 직접 할당하지 말 것. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private Short failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ---- 계정 프로필 (팀 스키마 26-10-19: 활성 계정은 아래 네 값이 모두 있어야 한다 - DB CHECK) ----
    // 탈퇴하면 DB 함수 withdraw_user()가 이 값들을 NULL로 파기한다.

    /** 가입 화면에서 입력한 이름. 본인(PATIENT) 가입이면 복약자 이름과 같다 */
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** AES-GCM으로 암호화한 휴대폰 번호. 엔티티 로그/toString에 노출하지 말 것 */
    @Column(name = "phone_enc")
    private byte[] phoneEnc;

    /** HMAC-SHA256(정규화된 번호) - 같은 번호 중복 가입 검사용. 같은 유형(PATIENT/GUARDIAN) 안에서만 유니크 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Builder
    public AppUser(String loginId, String passwordHash, AccountType accountType,
                   String name, LocalDate birthDate, byte[] phoneEnc, String phoneHash) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.accountType = accountType;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneEnc = phoneEnc;
        this.phoneHash = phoneHash;
        this.status = AccountStatus.ACTIVE;
        this.failedLoginCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- 도메인 행위 (무분별한 setter 노출 대신 의미 있는 메서드로만 상태 변경) ----

    /** 로그인 실패 1회 누적. 잠금 임계치 판단은 서비스(정책) 계층에서. */
    public void increaseFailedLoginCount() {
        this.failedLoginCount = (short) (this.failedLoginCount + 1);
    }

    public void resetFailedLoginCount() {
        this.failedLoginCount = 0;
    }

    public void lockUntil(LocalDateTime until) {
        this.lockedUntil = until;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /** 로그인 성공 처리 - 실패 카운트 리셋 + 마지막 로그인 시각 갱신 */
    public void recordSuccessfulLogin() {
        this.lastLoginAt = LocalDateTime.now();
        resetFailedLoginCount();
        this.lockedUntil = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void softDelete() {
        this.status = AccountStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE && this.deletedAt == null;
    }

    public enum AccountType {
        PATIENT, GUARDIAN
    }

    public enum AccountStatus {
        ACTIVE, DELETED
    }

    @Override
    public String toString() {
        // 이름·휴대폰 등 개인정보가 로그에 그대로 찍히지 않도록 커스텀 toString
        return "AppUser{userId=" + userId + ", accountType=" + accountType + "}";
    }
}