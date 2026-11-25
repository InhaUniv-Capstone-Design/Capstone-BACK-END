package com.gamcho.yakyeon.domain.account.access;

import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import com.gamcho.yakyeon.domain.account.entity.Patient;

/**
 * 접근 확인을 통과한 결과. 이후 로직에서 "누구 자격으로 접근했는지"를 알 수 있다.
 *
 * @param patient 접근 대상 (활성 복약자만 여기까지 온다)
 * @param role    SELF(본인) 또는 GUARDIAN(보호자)
 * @param linkId  보호자 접근의 근거가 된 연동 ID (본인이면 null)
 * @param scope   보호자의 현재 권한 범위 (본인이면 null)
 */
public record PatientAccess(Patient patient, AccessRole role, Long linkId, GuardianLink.PermissionScope scope) {
}