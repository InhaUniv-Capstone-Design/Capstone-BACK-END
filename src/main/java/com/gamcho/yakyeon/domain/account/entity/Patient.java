package com.gamcho.yakyeon.domain.account.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 복약자 — FR-AUTH-009~018
 */
@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long patientId;

    /** 복약자 본인 계정. 앱 없이 약통만 쓰는 복약자는 null */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private AppUser user;

    @Column(name = "display_name", length = 50, nullable = false)
    private String displayName;

    /** 만 14세 미만 판정(FR-AUTH-011)에 사용 */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** 본인 인증용 전화번호(암호화 저장) */
    @Column(name = "phone_enc")
    private byte[] phoneEnc;

    /** 조회용 해시 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    /** 보호자가 먼저 등록한 경우의 등록자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdByUser;

    /** ACTIVE, DELETED */
    @Column(name = "status", length = 10, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}