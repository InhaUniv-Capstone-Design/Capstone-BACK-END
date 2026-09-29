package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 복약자 (patient) - 모든 건강 데이터의 기준
 * FR-AUTH-009, 011, 018, FR-MULTI-001~004
 */
@Entity
@Table(name = "patient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long patientId;

    /** 복약자 본인의 앱 계정. 앱 없이 약통만 쓰면 null */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /**
     * 암호화된 휴대폰 번호(AES-GCM 등). 암·복호화는 서비스 계층(별도 CryptoService)에서만 수행하고
     * 이 엔티티는 암호문 바이트만 다룬다 - 엔티티 로그/toString에 절대 노출 금지.
     */
    @Column(name = "phone_enc", nullable = false)
    private byte[] phoneEnc;

    /**
     * SHA-256(정규화된 번호) - 조회 전용, 역산 불가.
     * DB 컬럼이 CHAR(64)(고정 길이, PostgreSQL 내부명 bpchar)라서
     * columnDefinition 문자열만으론 부족하고, Hibernate가 스키마 검증 시 비교하는
     * JDBC 타입 코드 자체를 @JdbcTypeCode(SqlTypes.CHAR)로 명시해야 한다.
     * (columnDefinition은 DDL 생성용 문자열일 뿐, validate 모드의 타입 비교에는 반영 안 됨)
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Patient(AppUser user, String name, LocalDate birthDate, byte[] phoneEnc, String phoneHash, AppUser createdBy) {
        this.user = user;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneEnc = phoneEnc;
        this.phoneHash = phoneHash;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** 만 14세 미만 여부 - 법정대리인 동의 대상 판단 (FR-AUTH 관련) */
    public boolean isUnder14() {
        return birthDate.plusYears(14).isAfter(LocalDate.now());
    }

    public void linkAppUser(AppUser user) {
        this.user = user;
    }

    @Override
    public String toString() {
        // phoneEnc/phoneHash가 로그에 그대로 찍히지 않도록 커스텀 toString
        return "Patient{patientId=" + patientId + ", name=" + name + "}";
    }
}