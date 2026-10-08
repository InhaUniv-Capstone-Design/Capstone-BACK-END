package com.gamcho.yakyeon.domain.account.access;

/** access_audit_log.actor_role 값 (운영자 ADMIN은 아직 사용하지 않음) */
public enum AccessRole {
    SELF,       // 복약자 본인
    GUARDIAN    // 보호자
}