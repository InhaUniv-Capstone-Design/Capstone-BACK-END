package com.gamcho.yakyeon.domain.account.consent;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 약 등록·약통 등록 같은 "그 기능을 쓰기 전 동의"를 서비스 안에서 확인한다.
 * 동의는 요청한 사람 본인의 것을 본다 (보호자가 복약자의 약을 등록하면 보호자 본인이 동의 안내에서 동의한 것이다).
 * 접근 권한 확인(404·403)을 통과한 뒤에 부른다.
 */
@Component
@RequiredArgsConstructor
public class ConsentGuard {

    private final ActiveConsentLookup consents;

    /** 약 등록·수정 전 복약정보 수집·이용 동의(MEDICATION) */
    public void requireMedication(Long userId) {
        require(userId, ConsentRules.MEDICATION, "복약정보 수집·이용 동의가 필요합니다. 동의한 뒤 다시 시도해주세요.");
    }

    /** 약통 등록 전 스마트 약통 연결 동의(DEVICE) */
    public void requireDevice(Long userId) {
        require(userId, ConsentRules.DEVICE, "스마트 약통 연결 동의가 필요합니다. 동의한 뒤 다시 시도해주세요.");
    }

    private void require(Long userId, String termsType, String message) {
        if (!consents.hasAllActive(userId, List.of(termsType))) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED, message);
        }
    }
}