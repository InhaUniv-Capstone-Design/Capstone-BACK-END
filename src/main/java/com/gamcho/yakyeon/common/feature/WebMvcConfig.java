package com.gamcho.yakyeon.common.feature;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 웹 MVC 공통 설정. 지금은 옛 방식 차단 인터셉터만 등록한다. */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LegacyConsentInterceptor legacyConsentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(legacyConsentInterceptor);
    }
}