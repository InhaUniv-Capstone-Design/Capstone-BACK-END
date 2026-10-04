package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 보호자 전용 기능에서 공통으로 쓰는 "호출자가 활성 GUARDIAN 계정인지" 확인.
 * 토큰이 유효해도 그 사이 탈퇴한 계정일 수 있어서, 토큰의 account_type만 믿지 않고
 * 매번 DB에서 활성 계정을 다시 조회한다.
 */
@Component
@RequiredArgsConstructor
public class GuardianAccessChecker {

    private final AppUserRepository appUserRepository;

    public AppUser requireGuardian(Long userId) {
        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getAccountType() != AppUser.AccountType.GUARDIAN) {
            throw new BusinessException(ErrorCode.GUARDIAN_ONLY);
        }
        return user;
    }
}