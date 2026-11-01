package com.gamcho.yakyeon.domain.account.consent;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 서비스 필수 동의(이용약관, 개인정보, 건강정보)를 철회한 사용자의 건강정보 API 호출을 막는다.
 *
 * 로그인한 요청에서만 확인하고(로그인 전 요청은 보안 설정이 따로 처리한다), @ConsentExempt가 붙은 API는
 * 확인하지 않는다. 컨트롤러가 실행되기 전에 막아서 입력 검증보다 앞선다.
 * 요청마다 동의 행을 한 번 조회한다(기본 키로 찾는 짧은 조회).
 */
@Component
@RequiredArgsConstructor
public class RequiredConsentInterceptor implements HandlerInterceptor {

    private final ActiveConsentLookup consents;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isExempt(method)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return true;
        }
        if (!consents.hasAllActive(userId, ConsentRules.SERVICE_REQUIRED)) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED,
                    "서비스 이용에 필요한 동의(이용약관, 개인정보, 건강정보)가 철회되어 있습니다. "
                            + "설정의 서비스 안내 및 정보 동의에서 다시 동의해주세요.");
        }
        return true;
    }

    private static boolean isExempt(HandlerMethod method) {
        return method.hasMethodAnnotation(ConsentExempt.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), ConsentExempt.class);
    }
}