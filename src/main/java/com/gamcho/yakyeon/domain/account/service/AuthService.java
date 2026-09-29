package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.dto.AgreementItem;
import com.gamcho.yakyeon.domain.account.dto.SignupRequest;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.TermsVersion;
import com.gamcho.yakyeon.domain.account.entity.UserAgreement;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.TermsVersionRepository;
import com.gamcho.yakyeon.domain.account.repository.UserAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 회원가입 시점에 확인하는 약관 종류.
     * DELEGATION/LEGAL_REP은 보호자 연동(guardian_link) 단계에서 별도로 동의를 받으므로
     * 여기서는 다루지 않는다.
     */
    private static final List<TermsVersion.TermsType> SIGNUP_TERMS_TYPES = List.of(
            TermsVersion.TermsType.TOS,
            TermsVersion.TermsType.PRIVACY,
            TermsVersion.TermsType.SENSITIVE_HEALTH,
            TermsVersion.TermsType.SERVICE_NOTICE
    );

    private final AppUserRepository appUserRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * FR-AUTH-001~003, FR-AUTH-008: 회원가입
     * 1) 비밀번호/비밀번호확인 일치 확인
     * 2) 아이디 중복 확인 (활성 계정 기준 - 탈퇴 계정의 아이디는 재사용 가능해야 함)
     * 3) 필수 약관(TOS/PRIVACY/SENSITIVE_HEALTH/SERVICE_NOTICE) 전부 동의했는지 확인
     * 4) 비밀번호 해시 후 계정 저장 (평문은 절대 저장하지 않음, NFR-SEC-001)
     * 5) 약관 동의 내역(user_agreement) 저장
     */
    @Transactional
    public Long signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (appUserRepository.existsByLoginIdAndDeletedAtIsNull(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        // 계정을 만들기 전에 약관 동의부터 검증한다 - 검증 실패 시 계정이 생기지 않도록.
        List<AgreementPlan> agreementPlan = resolveAgreementPlan(request.getAgreements());

        AppUser user = AppUser.builder()
                .loginId(request.getLoginId())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .accountType(request.getAccountType())
                .build();
        AppUser savedUser = appUserRepository.save(user);

        List<UserAgreement> agreements = agreementPlan.stream()
                .map(plan -> UserAgreement.builder()
                        .user(savedUser)
                        .termsVersion(plan.termsVersion())
                        .agreed(plan.agreed())
                        .build())
                .toList();
        userAgreementRepository.saveAll(agreements);

        return savedUser.getUserId();
    }

    /** FR-AUTH-002: 아이디 중복 검사 (활성 계정 기준) */
    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        return !appUserRepository.existsByLoginIdAndDeletedAtIsNull(loginId);
    }

    /**
     * 회원가입 시점의 "현재 시행 중인 최신 약관 버전" 기준으로 요청 내용을 검증한다.
     * - 필수 약관인데 동의 항목이 아예 없거나 agreed=false면 REQUIRED_TERMS_NOT_AGREED
     * - 필수 약관인데 클라이언트가 들고 있는 버전이 이미 지난 버전이면 TERMS_VERSION_OUTDATED
     *   (동의 화면을 새로고침해서 최신 버전으로 다시 동의받아야 함)
     * - 선택 약관은 동의 항목이 없으면 그냥 건너뜀 (거부로 기록하지 않음)
     */
    private List<AgreementPlan> resolveAgreementPlan(List<AgreementItem> submitted) {
        Map<Long, AgreementItem> submittedById = submitted.stream()
                .collect(Collectors.toMap(AgreementItem::termsVersionId, item -> item, (a, b) -> a));

        Map<Long, TermsVersion> submittedVersions = termsVersionRepository
                .findAllById(submittedById.keySet())
                .stream()
                .collect(Collectors.toMap(TermsVersion::getTermsVersionId, tv -> tv));

        LocalDateTime now = LocalDateTime.now();
        List<AgreementPlan> plan = new ArrayList<>();

        for (TermsVersion.TermsType type : SIGNUP_TERMS_TYPES) {
            TermsVersion latest = termsVersionRepository
                    .findTopByTermsTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(type, now)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TERMS_NOT_CONFIGURED));

            AgreementItem matched = submittedById.get(latest.getTermsVersionId());

            if (matched == null) {
                if (!Boolean.TRUE.equals(latest.getIsRequired())) {
                    continue; // 선택 약관 무응답 - 기록하지 않고 넘어감
                }
                boolean referencesStaleVersionOfSameType = submittedVersions.values().stream()
                        .anyMatch(tv -> tv.getTermsType() == type
                                && !tv.getTermsVersionId().equals(latest.getTermsVersionId()));
                throw new BusinessException(referencesStaleVersionOfSameType
                        ? ErrorCode.TERMS_VERSION_OUTDATED
                        : ErrorCode.REQUIRED_TERMS_NOT_AGREED);
            }

            if (Boolean.TRUE.equals(latest.getIsRequired()) && !Boolean.TRUE.equals(matched.agreed())) {
                throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
            }

            plan.add(new AgreementPlan(latest, matched.agreed()));
        }

        return plan;
    }

    private record AgreementPlan(TermsVersion termsVersion, Boolean agreed) {
    }
}