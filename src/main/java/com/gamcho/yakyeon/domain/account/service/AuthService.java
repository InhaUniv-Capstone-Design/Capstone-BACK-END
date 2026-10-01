package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.dto.AccountDeleteRequest;
import com.gamcho.yakyeon.domain.account.dto.AgreementItem;
import com.gamcho.yakyeon.domain.account.dto.LoginRequest;
import com.gamcho.yakyeon.domain.account.dto.LoginResponse;
import com.gamcho.yakyeon.domain.account.dto.PasswordChangeRequest;
import com.gamcho.yakyeon.domain.account.dto.RefreshRequest;
import com.gamcho.yakyeon.domain.account.dto.SignupRequest;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.AuthToken;
import com.gamcho.yakyeon.domain.account.entity.TermsVersion;
import com.gamcho.yakyeon.domain.account.entity.UserAgreement;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.AuthTokenRepository;
import com.gamcho.yakyeon.domain.account.repository.TermsVersionRepository;
import com.gamcho.yakyeon.domain.account.repository.UserAgreementRepository;
import com.gamcho.yakyeon.security.RefreshTokenGenerator;
import com.gamcho.yakyeon.security.TokenHasher;
import com.gamcho.yakyeon.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    /** 회원가입 시점에 확인하는 약관 종류 (DELEGATION/LEGAL_REP은 보호자 연동 단계에서 별도 처리) */
    private static final List<TermsVersion.TermsType> SIGNUP_TERMS_TYPES = List.of(
            TermsVersion.TermsType.TOS,
            TermsVersion.TermsType.PRIVACY,
            TermsVersion.TermsType.SENSITIVE_HEALTH,
            TermsVersion.TermsType.SERVICE_NOTICE
    );

    private final AppUserRepository appUserRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenHasher tokenHasher;

    @Value("${jwt.refresh-token-expiration-seconds:1209600}")
    private long refreshTokenExpirationSeconds;

    // ==================== 회원가입 ====================

    @Transactional
    public Long signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (appUserRepository.existsByLoginIdAndDeletedAtIsNull(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

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
                    continue;
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

    // ==================== 로그인 / 토큰 ====================

    /**
     * FR-AUTH-005~007: 로그인
     * 실패 횟수 잠금 정책은 적용하지 않기로 함(단순 아이디/비밀번호 검증만 수행).
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByLoginIdAndDeletedAtIsNull(request.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin();

        return issueTokens(user);
    }

    /**
     * FR-AUTH-006: Refresh Token으로 Access Token 재발급.
     * 매번 회전(rotation)한다 - 기존 Refresh Token은 1회용으로 즉시 폐기하고 새 걸 내준다.
     * 이미 폐기된(=한 번 쓰인) 토큰이 다시 들어오면 탈취로 의심하고 해당 유저의
     * 모든 세션(Refresh Token)을 무효화한다.
     */
    /**
     * noRollbackFor 필요: 재사용이 감지되면 revokeAllTokensFor()로 전체 토큰을 폐기한 뒤
     * BusinessException을 던지는데, 기본 @Transactional이면 예외 발생 시 트랜잭션이 롤백되면서
     * 방금 처리한 "전체 폐기"까지 같이 없었던 일이 돼버린다 (실제로 테스트에서 이 버그로 걸림 -
     * 재사용된 토큰만 막히고 다른 토큰은 여전히 살아있는 상태가 나왔었음).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse refresh(RefreshRequest request) {
        String hash = tokenHasher.sha256Hex(request.getRefreshToken());
        AuthToken token = authTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (token.getRevokedAt() != null) {
            revokeAllTokensFor(token.getUser().getUserId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        AppUser user = token.getUser();
        token.revoke();

        return issueTokens(user);
    }

    /** FR-AUTH-006: 로그아웃 - 전달받은 Refresh Token 하나만 폐기. 멱등하게 처리(이미 없어도 에러 아님). */
    @Transactional
    public void logout(RefreshRequest request) {
        String hash = tokenHasher.sha256Hex(request.getRefreshToken());
        authTokenRepository.findByTokenHash(hash).ifPresent(AuthToken::revoke);
    }

    // ==================== 비밀번호 변경 / 계정 삭제 ====================

    /**
     * 비밀번호 변경. 현재 비밀번호를 재확인한 뒤에만 허용한다.
     * 변경 성공 시 다른 기기에 남아있던 세션(Refresh Token)을 전부 폐기한다 -
     * 비밀번호를 바꾼 이유가 "계정이 털린 것 같아서"일 수 있는데, 그 상태에서
     * 공격자의 세션이 계속 살아있으면 비밀번호 변경이 의미가 없어진다.
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        revokeAllTokensFor(userId);
    }

    /**
     * 계정 삭제(탈퇴) - 논리 삭제(app_user.status=DELETED, deleted_at 기록)만 수행한다.
     * 물리 삭제를 하지 않는 이유는 지난번 DB 설계 논의에서 정리한 그대로다:
     * 탈퇴 후에도 남아야 하는 법적 근거 데이터(동의 이력 등)가 FK 연쇄로 같이
     * 사라지는 걸 막기 위함.
     */
    @Transactional
    public void deleteAccount(Long userId, AccountDeleteRequest request) {
        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        user.softDelete();
        revokeAllTokensFor(userId);
    }

    private LoginResponse issueTokens(AppUser user) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String rawRefreshToken = refreshTokenGenerator.generate();

        AuthToken token = AuthToken.builder()
                .user(user)
                .tokenHash(tokenHasher.sha256Hex(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationSeconds))
                .build();
        authTokenRepository.save(token);

        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                user.getUserId(),
                user.getAccountType().name()
        );
    }

    private void revokeAllTokensFor(Long userId) {
        authTokenRepository.findByUser_UserIdAndRevokedAtIsNull(userId)
                .forEach(AuthToken::revoke);
    }

    private record AgreementPlan(TermsVersion termsVersion, Boolean agreed) {
    }
}