package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.dto.ProfileResponse;
import com.gamcho.yakyeon.domain.account.dto.ProfileUpdateRequest;
import com.gamcho.yakyeon.domain.account.logic.PhoneMasker;
import com.gamcho.yakyeon.domain.account.repository.AccountQueryRepository;
import com.gamcho.yakyeon.security.PhoneEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 조회·수정 (설정 > 프로필, 홈 인사말). 로그인한 본인의 정보만 다루며 남의 번호를 받지 않는다.
 * 휴대폰 번호는 암호문을 복호화해서 바로 가린 뒤 돌려주고, 평문은 저장하거나 기록하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final AccountQueryRepository accountQueryRepository;
    private final PhoneEncryptor phoneEncryptor;

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return toResponse(userId);
    }

    @Transactional
    public ProfileResponse updateName(Long userId, ProfileUpdateRequest request) {
        // 공백만 있는 이름은 @NotBlank가 이미 걸러서, 여기서는 앞뒤 공백만 정리한다
        String name = request.getName().trim();
        if (accountQueryRepository.updateName(userId, name) == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponse(userId);
    }

    private ProfileResponse toResponse(Long userId) {
        AccountQueryRepository.ProfileRow row = accountQueryRepository.findProfile(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String phone = row.phoneEnc() == null ? null : phoneEncryptor.decrypt(row.phoneEnc());
        return new ProfileResponse(row.userId(), row.loginId(), row.accountType(), row.name(), row.birthDate(),
                PhoneMasker.mask(phone));
    }
}