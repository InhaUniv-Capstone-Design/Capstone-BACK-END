package com.gamcho.yakyeon.domain.account.access;

import com.gamcho.yakyeon.domain.account.entity.GuardianLink;

/**
 * 복약자 데이터에 대한 행동 종류. access_audit_log.action의 허용값과 정확히 같아야 한다.
 * 각 행동이 보호자에게 요구하는 최소 권한 범위도 함께 정의한다
 * (조회·내보내기·공유는 읽기 전용으로 충분하고, 등록·수정·삭제는 읽기·쓰기 권한이 필요).
 */
public enum AccessAction {
    VIEW(GuardianLink.PermissionScope.READ_ONLY),
    CREATE(GuardianLink.PermissionScope.READ_WRITE),
    UPDATE(GuardianLink.PermissionScope.READ_WRITE),
    DELETE(GuardianLink.PermissionScope.READ_WRITE),
    EXPORT(GuardianLink.PermissionScope.READ_ONLY),
    SHARE(GuardianLink.PermissionScope.READ_ONLY);

    private final GuardianLink.PermissionScope requiredScope;

    AccessAction(GuardianLink.PermissionScope requiredScope) {
        this.requiredScope = requiredScope;
    }

    public GuardianLink.PermissionScope requiredScope() {
        return requiredScope;
    }

    /**
     * 복약자 "본인"의 접근 중 기록해야 하는 것. DB 명세 기준으로 본인의 일반 조회·수정은 기록하지 않고,
     * 내보내기·공유는 모두 기록한다 (건강정보가 앱 밖으로 나가는 행동이라서).
     */
    public boolean isAuditedWhenSelf() {
        return this == EXPORT || this == SHARE;
    }
}