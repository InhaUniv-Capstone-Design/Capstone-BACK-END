package com.gamcho.yakyeon.domain.account.logic;

import java.time.OffsetDateTime;

/**
 * 탈퇴 데이터 파기의 보관 기간 정책 (스프링·DB 없는 순수 로직). 설정값을 안전한 범위로 고치고 기준 시각을 계산한다.
 *
 *  - 복약자 본인 탈퇴: patientRetentionDays 뒤에 삭제한다. 0이면 다음 점검 때 바로 삭제한다 ("본인 탈퇴 시 즉시 삭제").
 *  - 대리인(보호자) 탈퇴: 계정 행을 guardianRetentionDays 동안 보관한다. 이 행에 달린 연동 기록(해제 시각)이
 *    아래 "관리자를 잃은 복약자"의 보관 기간을 재는 시계 역할을 하므로, 계정 행이 먼저 지워지면 안 된다.
 *  - 관리하는 대리인이 모두 탈퇴한 앱 계정 없는 복약자: 마지막 연동이 해제된 지 abandonedPatientDays가 지나면 기록을 삭제한다
 *    ("대리인이 탈퇴해도 복용자 기록 30일 보관"). 시계가 사라지지 않게 guardianRetentionDays를 넘지 못한다.
 */
public record PurgePolicy(int patientRetentionDays, int guardianRetentionDays, int abandonedPatientDays, int batchLimit) {

    public static final int MAX_DAYS = 3650;
    public static final int MAX_BATCH = 1000;

    /** 잘못된 설정(음수, 너무 큰 값)을 안전한 값으로 고쳐 만든다 */
    public static PurgePolicy of(int patientDays, int guardianDays, int abandonedDays, int batch) {
        int patient = clamp(patientDays, 0, MAX_DAYS);
        int guardian = clamp(guardianDays, 0, MAX_DAYS);
        int abandoned = Math.min(clamp(abandonedDays, 0, MAX_DAYS), guardian);
        return new PurgePolicy(patient, guardian, abandoned, clamp(batch, 1, MAX_BATCH));
    }

    /** 이 시각보다 먼저 탈퇴한 복약자 계정·프로필을 삭제한다 */
    public OffsetDateTime patientCutoff(OffsetDateTime now) {
        return now.minusDays(patientRetentionDays);
    }

    /** 이 시각보다 먼저 탈퇴한 대리인 계정을 삭제한다 */
    public OffsetDateTime guardianCutoff(OffsetDateTime now) {
        return now.minusDays(guardianRetentionDays);
    }

    /** 마지막 연동이 이 시각보다 먼저 해제된, 관리자를 잃은 복약자를 삭제 대상으로 본다 */
    public OffsetDateTime abandonedCutoff(OffsetDateTime now) {
        return now.minusDays(abandonedPatientDays);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}