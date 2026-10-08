package com.gamcho.yakyeon.domain.account.access;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.GuardianLinkRepository;
import com.gamcho.yakyeon.domain.account.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 복약자의 건강 데이터에 접근하기 전에 반드시 통과해야 하는 관문 (FR-SERVER-006).
 * 약물·스케줄·약통·복용 기록·알림·내보내기 등 환자 소유 데이터를 다루는 모든 API가 맨 앞에서 호출한다.
 *
 * 토큰에는 "이 사용자가 누구인가"만 들어 있고 연동 상태나 권한 범위는 들어 있지 않다.
 * 그래서 요청마다 DB에서 현재 연동과 권한을 확인한다. 철회·권한 낮추기가 즉시 효력을 갖는 이유다.
 *
 * 판정 순서
 *  1) 계정이 살아 있는가
 *  2) 대상 복약자가 활성(ACTIVE)인가 - 탈퇴한 복약자의 데이터는 접근 불가
 *  3) 복약자 본인인가 - 통과
 *  4) 연동(ACTIVE)이 있는 보호자인가, 그 권한 범위가 행동에 충분한가
 *
 * 응답 코드: 대상이 없거나 연동이 전혀 없으면 둘 다 404(PATIENT_NOT_FOUND)로 똑같이 응답해서
 * "그 복약자가 존재하는지"가 드러나지 않게 한다. 연동은 있는데 권한이 모자란 경우만 403.
 *
 * 접근 기록(access_audit_log): 보호자의 허용된 접근, 모든 거부, 본인의 내보내기·공유를 남긴다.
 * 본인의 일반 조회·수정은 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PatientAccessGuard {

    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final AccessAuditWriter auditWriter;

    /**
     * @param resourceType 접근하는 데이터 종류 (예: "patient", "patient_medication") - 접근 기록에 남는다
     * @param resourceId   접근하는 데이터 ID (목록 조회처럼 특정 행이 없으면 null)
     * @throws BusinessException 접근 불가 시 (PATIENT_NOT_FOUND 404 또는 INSUFFICIENT_PERMISSION 403)
     */
    public PatientAccess check(Long userId, Long patientId, AccessAction action,
                               String resourceType, Long resourceId) {
        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 계정 유형으로 기록용 역할을 정한다. 복약자 계정이 남의 데이터를 건드리려 한 경우도 SELF로 기록되지만
        // result가 DENIED라 구분된다.
        AccessRole actorRole = user.getAccountType() == AppUser.AccountType.GUARDIAN
                ? AccessRole.GUARDIAN : AccessRole.SELF;

        Patient patient = patientRepository.findByPatientIdAndStatus(patientId, Patient.PatientStatus.ACTIVE)
                .orElse(null);
        if (patient == null) {
            deny(userId, actorRole, patientId, null, action, resourceType, resourceId);
            throw new BusinessException(ErrorCode.PATIENT_NOT_FOUND);
        }

        // 복약자 본인
        if (patient.getUser() != null && patient.getUser().getUserId().equals(userId)) {
            if (action.isAuditedWhenSelf()) {
                auditWriter.write(userId, AccessRole.SELF, patientId, null,
                        action, resourceType, resourceId, true);
            }
            return new PatientAccess(patient, AccessRole.SELF, null, null);
        }

        // 보호자: 활성 연동이 있어야 한다 (대기·거부·철회된 연동은 접근 불가)
        GuardianLink link = guardianLinkRepository
                .findByGuardianUser_UserIdAndPatient_PatientIdAndStatusIn(
                        userId, patientId, List.of(GuardianLink.LinkStatus.ACTIVE))
                .orElse(null);
        if (link == null) {
            deny(userId, actorRole, patientId, null, action, resourceType, resourceId);
            throw new BusinessException(ErrorCode.PATIENT_NOT_FOUND);
        }

        if (!link.permits(action.requiredScope())) {
            deny(userId, actorRole, patientId, link.getLinkId(), action, resourceType, resourceId);
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        auditWriter.write(userId, AccessRole.GUARDIAN, patientId, link.getLinkId(),
                action, resourceType, resourceId, true);
        return new PatientAccess(patient, AccessRole.GUARDIAN, link.getLinkId(), link.getPermissionScope());
    }

    private void deny(Long userId, AccessRole role, Long patientId, Long linkId,
                      AccessAction action, String resourceType, Long resourceId) {
        auditWriter.write(userId, role, patientId, linkId, action, resourceType, resourceId, false);
    }
}