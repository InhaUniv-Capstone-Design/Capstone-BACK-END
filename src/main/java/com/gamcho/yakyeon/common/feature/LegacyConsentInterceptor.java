package com.gamcho.yakyeon.common.feature;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @LegacyConsentOnly가 붙은 엔드포인트를 설정이 꺼져 있으면 404로 막는다.
 * 컨트롤러 메서드가 실행되기 전(입력 검증보다 앞)에 막기 때문에, 꺼진 API가 "요청 형식이 틀렸다(400)" 같은
 * 응답으로 존재를 드러내지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LegacyConsentInterceptor implements HandlerInterceptor {

    private final LegacyConsentFeature feature;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && isLegacy(method)) {
            feature.requireEnabled();
        }
        return true;
    }

    private static boolean isLegacy(HandlerMethod method) {
        return method.hasMethodAnnotation(LegacyConsentOnly.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), LegacyConsentOnly.class);
    }
}