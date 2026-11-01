package com.gamcho.yakyeon.domain.account.consent;

import java.util.Collection;

/**
 * "이 사용자가 이 종류의 약관에 지금 유효하게 동의했는가"를 알려 주는 조회. 인터셉터와 서비스 가드가 쓴다.
 * 구현은 AgreementRepository(DB)이고, 테스트에서는 간단한 람다로 바꿔 쓴다.
 */
public interface ActiveConsentLookup {

    /** 주어진 약관 종류 모두에 지금 유효한 동의(동의했고 철회하지 않음)가 있으면 true. 버전은 따지지 않는다 */
    boolean hasAllActive(long userId, Collection<String> termsTypes);
}