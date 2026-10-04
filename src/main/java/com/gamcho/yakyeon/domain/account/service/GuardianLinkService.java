package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.PhoneNumbers;
import com.gamcho.yakyeon.domain.account.dto.ConsentRequest;
import com.gamcho.yakyeon.domain.account.dto.ConsentResponse;
import com.gamcho.yakyeon.domain.account.dto.GuardianLinkResponse;
import com.gamcho.yakyeon.domain.account.dto.PatientSearchResponse;
import com.gamcho.yakyeon.domain.account.dto.PermissionChangeResponse;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.ConsentLog;
import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import com.gamcho.yakyeon.domain.account.entity.TermsVersion;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.ConsentLogRepository;
import com.gamcho.yakyeon.domain.account.repository.GuardianLinkRepository;
import com.gamcho.yakyeon.domain.account.repository.PatientRepository;
import com.gamcho.yakyeon.domain.account.repository.PhoneVerificationRepository;
import com.gamcho.yakyeon.domain.account.repository.TermsVersionRepository;
import com.gamcho.yakyeon.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GuardianLinkService {

    /** 문자 인증을 마친 뒤 이 시간 안에 동의를 제출해야 한다 */
    private static final long VERIFIED_VALID_MINUTES = 10;

    private final GuardianAccessChecker guardianAccessChecker;
    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final ConsentLogRepository consentLogRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final TokenHasher tokenHasher;

    /**
     * 전화번호로 기존 복약자 조회 (연동 요청을 보내기 전 대상 찾기).
     *
     * ⚠️ 프라이버시 주의: 번호를 넣어보는 것만으로 "이 번호가 약연 복약자인지"가 드러난다.
     * 그래서 이름은 마스킹하고 최소 정보만 돌려준다. 호출 횟수 제한(rate limit)은
     * 아직 없다 - 공개 배포 전에 추가해야 한다.
     */
    @Transactional(readOnly = true)
    public List<PatientSearchResponse> searchPatients(Long userId, String rawPhone) {
        guardianAccessChecker.requireGuardian(userId);

        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isValidMobile(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        return patientRepository.findAllByPhoneHash(tokenHasher.sha256Hex(phone)).stream()
                .map(p -> new PatientSearchResponse(p.getPatientId(), maskName(p.getName()), p.getUser() != null))
                .toList();
    }

    /** 대리 관리 요청 생성 (status=PENDING). 이후 문자 인증 → 동의 단계로 이어진다. */
    @Transactional
    public GuardianLinkResponse createLink(Long userId, Long patientId) {
        AppUser guardian = guardianAccessChecker.requireGuardian(userId);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATIENT_NOT_FOUND));

        boolean alreadyOpen = guardianLinkRepository
                .findByGuardianUser_UserIdAndPatient_PatientIdAndStatusIn(
                        userId, patientId,
                        List.of(GuardianLink.LinkStatus.PENDING, GuardianLink.LinkStatus.ACTIVE))
                .isPresent();
        if (alreadyOpen) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_EXISTS);
        }

        GuardianLink link = GuardianLink.builder()
                .guardianUser(guardian)
                .patient(patient)
                .permissionScope(GuardianLink.PermissionScope.READ_WRITE) // DB 기본값과 동일
                .build();
        GuardianLink saved = guardianLinkRepository.save(link);

        return new GuardianLinkResponse(saved.getLinkId(), saved.getStatus().name());
    }

    /**
     * 위임 동의/거부.
     *
     * GRANT는 아래를 모두 만족해야 한다:
     *  - 내 연동 요청이고 아직 PENDING
     *  - 이 연동 요청에 속한 인증(link_id 일치)이고, 인증 완료 후 10분 이내이며,
     *    목적(DELEGATION / 만 14세 미만은 LEGAL_REP)이 맞음
     *  - 만 14세 미만이면 법정대리인 이름 필수
     * 성공하면 guardian_link를 ACTIVE로 바꾸고 consent_log(append-only)에 이력을 남긴다.
     */
    @Transactional
    public ConsentResponse consent(Long userId, Long linkId, ConsentRequest request) {
        guardianAccessChecker.requireGuardian(userId);

        GuardianLink link = guardianLinkRepository.findByLinkIdAndGuardianUser_UserId(linkId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND));
        if (link.getStatus() != GuardianLink.LinkStatus.PENDING) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_PENDING);
        }

        Patient patient = link.getPatient();
        boolean minor = patient.isUnder14();

        TermsVersion.TermsType termsType = minor ? TermsVersion.TermsType.LEGAL_REP : TermsVersion.TermsType.DELEGATION;
        TermsVersion terms = latestTerms(termsType);

        if (request.getAction() == ConsentRequest.Decision.REJECT) {
            link.reject();
            // REJECT는 "누가 동의했는가"가 아니라 대기 중인 요청을 닫는 기록이라 subject는 SELF로 둔다
            // (LEGAL_REP으로 기록하려면 이름이 필수인데, 거부 시에는 받지 않는다).
            saveLog(link, patient, userId, ConsentLog.Action.REJECT,
                    GuardianLink.ConsentSubject.SELF, null, terms, null);
            return new ConsentResponse(link.getStatus().name());
        }

        // ---- GRANT ----
        if (request.getVerificationId() == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_REQUIRED);
        }
        PhoneVerification verification = phoneVerificationRepository.findById(request.getVerificationId())
                // 다른 연동 요청의 인증이면 존재 여부 자체를 숨기려고 NOT_FOUND로 통일
                .filter(v -> Objects.equals(v.getLinkId(), link.getLinkId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (!verification.isVerified()) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_COMPLETED);
        }
        if (verification.getVerifiedAt().plusMinutes(VERIFIED_VALID_MINUTES).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        PhoneVerification.Purpose expectedPurpose =
                minor ? PhoneVerification.Purpose.LEGAL_REP : PhoneVerification.Purpose.DELEGATION;
        if (verification.getPurpose() != expectedPurpose) {
            throw new BusinessException(ErrorCode.VERIFICATION_PURPOSE_MISMATCH);
        }

        String legalRepName = null;
        if (minor) {
            if (request.getLegalRepName() == null || request.getLegalRepName().isBlank()) {
                throw new BusinessException(ErrorCode.LEGAL_REP_NAME_REQUIRED);
            }
            legalRepName = request.getLegalRepName().trim();
        }

        GuardianLink.ConsentSubject subject =
                minor ? GuardianLink.ConsentSubject.LEGAL_REP : GuardianLink.ConsentSubject.SELF;
        link.activate(subject);
        saveLog(link, patient, userId, ConsentLog.Action.GRANT, subject, legalRepName, terms,
                verification.getVerificationId());

        return new ConsentResponse(link.getStatus().name());
    }

    /**
     * 로그인 기반 철회·연동 해제. 연동의 당사자 두 쪽이 호출할 수 있다:
     *  - 그 연동의 보호자 본인 (연동을 끊음)
     *  - 앱 계정이 있는 복약자 본인 (자기 데이터 접근을 끊음)
     * 둘 다 아니면 존재 여부를 숨기려고 NOT_FOUND로 응답한다.
     * ACTIVE 연동만 대상이다 (PENDING은 REJECT로 닫는다).
     */
    @Transactional
    public void revoke(Long userId, Long linkId) {
        appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GuardianLink link = guardianLinkRepository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND));

        boolean isGuardian = link.getGuardianUser().getUserId().equals(userId);
        Patient patient = link.getPatient();
        boolean isPatientSelf = patient.getUser() != null && patient.getUser().getUserId().equals(userId);
        if (!isGuardian && !isPatientSelf) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND);
        }
        if (link.getStatus() != GuardianLink.LinkStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_ACTIVE);
        }

        revokeLink(link, null);
    }

    /**
     * 보호자 권한 범위 변경 (FR-AUTH-015).
     *  - 복약자 본인(앱 계정): 올리기/내리기 모두 가능
     *  - 연동의 보호자: 낮추기(READ_WRITE → READ_ONLY)만 가능. 올리면 복약자의 동의 없이
     *    권한이 커지는 셈이라 막는다.
     *  - 앱 계정이 없는 복약자의 연동은 복약자가 직접 바꿀 수단이 없어서, 보호자가 낮추는 것만 가능하다.
     * 이미 같은 값이면 아무것도 바꾸지 않고(이력도 남기지 않고) 현재 값을 돌려준다.
     */
    @Transactional
    public PermissionChangeResponse changePermission(Long userId, Long linkId,
                                                     GuardianLink.PermissionScope newScope) {
        appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GuardianLink link = guardianLinkRepository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND));

        boolean isGuardian = link.getGuardianUser().getUserId().equals(userId);
        Patient patient = link.getPatient();
        boolean isPatientSelf = patient.getUser() != null && patient.getUser().getUserId().equals(userId);
        if (!isGuardian && !isPatientSelf) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND);
        }
        if (link.getStatus() != GuardianLink.LinkStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_ACTIVE);
        }

        GuardianLink.PermissionScope current = link.getPermissionScope();
        if (current == newScope) {
            return new PermissionChangeResponse(link.getLinkId(), current.name());
        }

        boolean raising = current == GuardianLink.PermissionScope.READ_ONLY
                && newScope == GuardianLink.PermissionScope.READ_WRITE;
        if (raising && !isPatientSelf) {
            throw new BusinessException(ErrorCode.PERMISSION_UPGRADE_FORBIDDEN);
        }

        TermsVersion terms = latestTerms(
                patient.isUnder14() ? TermsVersion.TermsType.LEGAL_REP : TermsVersion.TermsType.DELEGATION);

        link.changePermissionScope(newScope);
        // consent_log.permission_scope는 "이 시점의 권한 범위" - 변경 후 값이 기록된다
        saveLog(link, patient, link.getGuardianUser().getUserId(), ConsentLog.Action.SCOPE_CHANGE,
                GuardianLink.ConsentSubject.SELF, null, terms, null);

        return new PermissionChangeResponse(link.getLinkId(), newScope.name());
    }

    /**
     * 연동 하나를 철회 처리하고 consent_log(REVOKE)에 이력을 남긴다.
     * 로그인 기반 철회(verificationId=null)와 문자 인증 철회(RevocationService)가 공통으로 쓴다.
     *
     * consent_log에는 "누가 철회했는지"를 구분하는 컬럼이 없다 (consent_subject는 동의 주체이고
     * guardian_user_id는 연동의 보호자). 문자 인증 철회는 verification_id로 구분되지만,
     * 보호자가 끊은 건지 복약자가 로그인해서 끊은 건지는 이력만으로는 알 수 없다.
     */
    @Transactional
    public void revokeLink(GuardianLink link, Long verificationId) {
        Patient patient = link.getPatient();
        TermsVersion terms = latestTerms(
                patient.isUnder14() ? TermsVersion.TermsType.LEGAL_REP : TermsVersion.TermsType.DELEGATION);

        link.revoke();
        saveLog(link, patient, link.getGuardianUser().getUserId(), ConsentLog.Action.REVOKE,
                GuardianLink.ConsentSubject.SELF, null, terms, verificationId);
    }

    private TermsVersion latestTerms(TermsVersion.TermsType type) {
        return termsVersionRepository
                .findTopByTermsTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(type, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.TERMS_NOT_CONFIGURED));
    }

    private void saveLog(GuardianLink link, Patient patient, Long guardianUserId, ConsentLog.Action action,
                         GuardianLink.ConsentSubject subject, String legalRepName, TermsVersion terms,
                         Long verificationId) {
        consentLogRepository.save(ConsentLog.builder()
                .patientId(patient.getPatientId())
                .guardianUserId(guardianUserId)
                .linkId(link.getLinkId())
                .action(action)
                .consentSubject(subject)
                .legalRepName(legalRepName)
                .permissionScope(link.getPermissionScope())
                .termsVersionId(terms.getTermsVersionId())
                .verificationId(verificationId)
                .build());
    }

    /** 홍길동 → 홍*동, 홍길 → 홍*, 홍 → * */
    static String maskName(String name) {
        int n = name.length();
        if (n <= 1) {
            return "*";
        }
        if (n == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(n - 2) + name.charAt(n - 1);
    }
}