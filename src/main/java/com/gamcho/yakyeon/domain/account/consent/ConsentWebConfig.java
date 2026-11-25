package com.gamcho.yakyeon.domain.account.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 필수 동의 확인 인터셉터를 등록한다. */
@Configuration
@RequiredArgsConstructor
public class ConsentWebConfig implements WebMvcConfigurer {

    private final RequiredConsentInterceptor requiredConsentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiredConsentInterceptor);
    }
}