package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.dto.SignupRequest;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * FR-AUTH-001~003: 회원가입
     * 1) 비밀번호/비밀번호확인 일치 확인
     * 2) 아이디 중복 확인
     * 3) 비밀번호 해시 후 저장 (평문은 절대 저장하지 않음, NFR-SEC-001)
     */
    @Transactional
    public Long signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (appUserRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        AppUser user = AppUser.builder()
                .loginId(request.getLoginId())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .accountType(request.getAccountType())
                .build();
        // status='ACTIVE', failedLoginCount=0 은 엔티티의 @Builder.Default 값이 자동 적용된다

        AppUser saved = appUserRepository.save(user);
        return saved.getUserId();
    }

    /** FR-AUTH-002: 아이디 중복 검사 */
    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        return !appUserRepository.existsByLoginId(loginId);
    }
}