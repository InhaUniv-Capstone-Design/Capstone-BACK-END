package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.consent.AgreementRepository;
import com.gamcho.yakyeon.domain.account.consent.ConsentRules;
import com.gamcho.yakyeon.domain.account.dto.AgreementRequest;
import com.gamcho.yakyeon.domain.account.dto.AgreementResponse;
import com.gamcho.yakyeon.domain.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 앱의 "서비스 안내 및 정보 동의" 화면: 약관 종류별 동의 상태 조회, 동의, 철회.
 * 철회의 효과(건강정보 차단, 약 등록 제한, 약통 해제)는 이 서비스가 아니라 각 기능이 동의를 확인하면서 나타난다.
 */
@Service
@RequiredArgsConstructor
public class AgreementService {

    private final AgreementRepository agreementRepository;
    private final DeviceService deviceService;

    /** 약관 종류별 현재 상태. 한 번도 동의하지 않은 종류는 목록에 없다 */
    @Transactional(readOnly = true)
    public List<AgreementResponse> list(Long userId) {
        return ConsentRules.currentStates(agreementRepository.findRows(userId)).stream()
                .map(AgreementResponse::from)
                .toList();
    }

    /**
     * 동의한다. 지금 시행 중인 최신 버전에만 동의할 수 있고(앱이 오래된 약관을 들고 있으면 409),
     * 이미 동의한 것을 다시 보내도 같은 결과를 돌려준다(이력도 다시 쌓지 않는다).
     */
    @Transactional
    public AgreementResponse agree(Long userId, AgreementRequest request) {
        if (!Boolean.TRUE.equals(request.getAgreed())) {
            throw new BusinessException(ErrorCode.TERMS_TYPE_NOT_SUPPORTED,
                    "이 API는 동의에만 쓸 수 있습니다. 동의를 철회하려면 철회 API를 사용해주세요.");
        }
        AgreementRepository.TermsRef terms = agreementRepository.findTerms(request.getTermsVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TERMS_VERSION_NOT_FOUND));
        if (!ConsentRules.isAgreeable(terms.termsType())) {
            throw new BusinessException(ErrorCode.TERMS_TYPE_NOT_SUPPORTED);
        }
        long latest = agreementRepository.latestEffectiveVersionId(terms.termsType()).orElse(-1L);
        if (latest != terms.termsVersionId()) {
            throw new BusinessException(ErrorCode.TERMS_VERSION_OUTDATED);
        }

        agreementRepository.agree(userId, terms.termsVersionId());

        return ConsentRules.currentStates(agreementRepository.findRows(userId)).stream()
                .filter(state -> state.termsType().equals(terms.termsType()))
                .findFirst()
                .map(AgreementResponse::from)
                .orElseThrow(() -> new IllegalStateException("동의를 저장한 직후 상태를 찾을 수 없습니다."));
    }

    /**
     * 철회한다. 이미 철회했거나 동의한 적이 없어도 오류 없이 끝난다(같은 요청을 다시 보내도 안전).
     * 약통 연결 동의(DEVICE)를 철회하면 본인에게 연결된 약통은 함께 해제한다.
     */
    @Transactional
    public void withdraw(Long userId, String termsType) {
        if (!ConsentRules.isWithdrawable(termsType)) {
            throw new BusinessException(ErrorCode.TERMS_TYPE_NOT_SUPPORTED);
        }
        agreementRepository.withdrawAllActive(userId, termsType);
        if (ConsentRules.DEVICE.equals(termsType)) {
            deviceService.releaseOwnDevice(userId);
        }
    }
}