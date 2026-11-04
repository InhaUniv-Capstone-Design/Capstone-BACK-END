package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.ratelimit.FixedWindowCounter;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.consent.AgreementRepository;
import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;
import com.gamcho.yakyeon.domain.account.dto.GuardianGrantRequest;
import com.gamcho.yakyeon.domain.account.dto.GuardianGrantResponse;
import com.gamcho.yakyeon.domain.account.dto.SharedItemsChangeRequest;
import com.gamcho.yakyeon.domain.account.dto.SharedItemsResponse;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.WebConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 복약자 웹 동의: 복약자가 열람 범위 6개 항목을 직접 고르고, 그 범위 안에서만 대리인(보호자)이 복약 정보를 보게 한다.
 *
 * 규칙
 *  - 복약자 계정만 호출한다. 만 14세 미만은 법정대리인 동의가 필요해서 이 화면에서는 동의할 수 없다(옛 방식과 같은 기준).
 *  - 동의 문구(DELEGATION 약관)는 지금 시행 중인 최신 버전만 받는다. 어떤 문구에 동의했는지가 이력에 정확히 남아야 한다.
 *  - 필수 3개(이름·연락처, 약물 정보, 사진)에 모두 동의해야 연동이 만들어진다(권한은 쓰기까지 READ_WRITE로 시작, 복약자가 나중에 낮출 수 있다).
 *    하나라도 비동의하면 연동 없이 "거절됨" 기록만 남긴다.
 *  - 대리인 아이디가 없거나, 보호자 계정이 아니거나, 탈퇴했다면 모두 같은 오류(GUARDIAN_NOT_FOUND)다. 아이디가 있는지 알아내는 데 쓰이지
 *    않도록 복약자 계정당 시간당 10번으로 제한한다.
 *  - 동의와 변경은 동의 이력(consent_log, 수정·삭제 불가)에 항목과 약관 버전까지 남는다.
 */
@Service
@RequiredArgsConstructor
public class WebConsentService {

    private static final String DELEGATION = "DELEGATION";
    private static final int GRANTS_PER_HOUR = 10;
    private static final long HOUR_SECONDS = 3600;

    private final AppUserRepository appUserRepository;
    private final WebConsentRepository webConsentRepository;
    private final AgreementRepository agreementRepository;

    /** 계정당 호출 횟수 (서버 메모리). 접속 주소별 제한(@RateLimit)과 함께 쓴다 */
    private final FixedWindowCounter grantLimiter = new FixedWindowCounter(20_000);

    /** 열람 범위 저장 (웹의 "열람 범위 저장" 버튼) */
    @Transactional
    public GuardianGrantResponse grant(Long userId, GuardianGrantRequest request) {
        AppUser user = requirePatientAccount(userId);
        if (!grantLimiter.tryAcquire("grant|" + userId, GRANTS_PER_HOUR, HOUR_SECONDS, Instant.now().getEpochSecond())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        WebConsentRepository.PatientRef patient = webConsentRepository.findOwnActivePatient(user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PATIENT_NOT_FOUND));
        requireNotUnder14(patient.birthDate());

        long termsVersionId = requireLatestDelegationTerms(request.getTermsVersionId());

        long guardianUserId = webConsentRepository.findActiveGuardianUserId(request.getGuardianLoginId().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_NOT_FOUND));
        if (webConsentRepository.liveLinkExists(guardianUserId, patient.patientId())) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_EXISTS);
        }

        String relationship = SharedItemRules.normalizeRelationship(request.getRelationship());
        List<String> granted = SharedItemRules.grantedItems(request.getConsents());

        try {
            if (!SharedItemRules.allRequiredGranted(granted)) {
                long linkId = webConsentRepository.insertRejectedLink(guardianUserId, patient.patientId(), relationship);
                webConsentRepository.insertConsentLog(patient.patientId(), guardianUserId, linkId, "REJECT",
                        "READ_ONLY", termsVersionId, SharedItemRules.toArrayLiteral(List.of()));
                return new GuardianGrantResponse(linkId, "REJECTED", null, List.of());
            }
            String items = SharedItemRules.toArrayLiteral(granted);
            long linkId = webConsentRepository.insertActiveLink(guardianUserId, patient.patientId(), relationship, items);
            webConsentRepository.insertConsentLog(patient.patientId(), guardianUserId, linkId, "GRANT",
                    "READ_WRITE", termsVersionId, items);
            return new GuardianGrantResponse(linkId, "ACTIVE", "READ_WRITE", granted);
        } catch (DuplicateKeyException e) {
            // 동시에 같은 연동을 두 번 만든 경우 (DB의 "살아 있는 연동은 하나" 제약이 막았다)
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_EXISTS);
        }
    }

    /**
     * 연동 후 열람 범위 변경. 복약자 본인만 할 수 있고, 필수 3개를 비동의로 바꾸는 요청은 거절한다
     * (연동을 끊으려면 연동 해제를 쓴다). 같은 범위로 다시 저장하면 아무것도 바꾸지 않고 이력도 남기지 않는다.
     */
    @Transactional
    public SharedItemsResponse changeSharedItems(Long userId, Long linkId, SharedItemsChangeRequest request) {
        appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WebConsentRepository.LinkRow link = webConsentRepository.lockOwnLink(linkId, userId)
                // 남의 연동이거나 없는 연동이면 존재 여부를 숨기려고 같은 오류로 응답한다
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND));
        if (!"ACTIVE".equals(link.status())) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_ACTIVE);
        }
        requireNotUnder14(link.patientBirthDate());

        List<String> granted = SharedItemRules.grantedItems(request.getConsents());
        if (!SharedItemRules.allRequiredGranted(granted)) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_ITEM);
        }

        List<String> current = SharedItemRules.parse(link.sharedItemsText());
        if (SharedItemRules.sameItems(current, granted)) {
            return new SharedItemsResponse(link.linkId(), link.status(), current);
        }

        long termsVersionId = agreementRepository.latestEffectiveVersionId(DELEGATION)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERMS_NOT_CONFIGURED));
        String items = SharedItemRules.toArrayLiteral(granted);
        webConsentRepository.updateSharedItems(link.linkId(), items);
        webConsentRepository.insertConsentLog(link.patientId(), link.guardianUserId(), link.linkId(), "SCOPE_CHANGE",
                link.permissionScope(), termsVersionId, items);
        return new SharedItemsResponse(link.linkId(), link.status(), granted);
    }

    private AppUser requirePatientAccount(Long userId) {
        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getAccountType() != AppUser.AccountType.PATIENT) {
            throw new BusinessException(ErrorCode.PATIENT_ONLY);
        }
        return user;
    }

    /** 만 14세 미만은 법정대리인 동의가 필요하다. 생년월일이 없는 경우(탈퇴 등)는 막지 않는다 */
    private static void requireNotUnder14(LocalDate birthDate) {
        if (birthDate != null && birthDate.plusYears(14).isAfter(ApiTime.today())) {
            throw new BusinessException(ErrorCode.LEGAL_REP_REQUIRED);
        }
    }

    /** 요청한 약관 버전이 DELEGATION이고 지금 시행 중인 최신 버전인지 확인한다 */
    private long requireLatestDelegationTerms(Long termsVersionId) {
        AgreementRepository.TermsRef terms = agreementRepository.findTerms(termsVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERMS_VERSION_NOT_FOUND));
        if (!DELEGATION.equals(terms.termsType())) {
            throw new BusinessException(ErrorCode.TERMS_TYPE_NOT_SUPPORTED);
        }
        long latest = agreementRepository.latestEffectiveVersionId(DELEGATION).orElse(-1L);
        if (latest != terms.termsVersionId()) {
            throw new BusinessException(ErrorCode.TERMS_VERSION_OUTDATED);
        }
        return terms.termsVersionId();
    }
}