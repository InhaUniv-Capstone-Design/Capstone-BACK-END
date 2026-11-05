package com.gamcho.yakyeon;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 로그인 없이 부를 수 있는 API가 의도한 것뿐인지 확인한다. 서버의 모든 API를 로그인 정보 없이 불러 보고,
 * 아래 "공개 API" 12개를 뺀 나머지가 모두 401(로그인 필요)인지 본다. 새 API를 만들고 보안 설정을 실수로 열어 두거나
 * 공개 목록에 없는 API를 열면 이 테스트가 실패한다. 공개 API를 일부러 늘릴 때는 SecurityConfig와 이 목록을 함께 고친다.
 * (로그인 정보 없이 보낸 요청은 보안 필터에서 거절되어 실제 처리가 일어나지 않는다.)
 */
@SpringBootTest
class EndpointAuthenticationTest {

    /** SecurityConfig의 permitAll과 같아야 한다. 메서드 + 경로 */
    private static final Set<String> PUBLIC = Set.of(
            "POST /auth/signup",
            "POST /auth/login",
            "POST /auth/refresh",
            "POST /auth/logout",
            "GET /auth/check-id",
            "POST /auth/phone-verifications",
            "POST /auth/phone-verifications/{verificationId}/confirm",
            "GET /terms",
            "POST /revocations/request",
            "POST /revocations/confirm",
            "POST /device/heartbeat",
            "POST /device/signals");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("공개 API 12개를 뺀 모든 API는 로그인 정보 없이 부르면 401이다")
    void everythingExceptThePublicEndpointsRequiresLogin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();

        Set<String> seenPublic = new HashSet<>();
        List<String> notProtected = new ArrayList<>();
        int checked = 0;
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            RequestMethod method = methods.isEmpty() ? RequestMethod.GET : methods.iterator().next();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                String key = method + " " + pattern;
                if (PUBLIC.contains(key)) {
                    seenPublic.add(key);
                    continue;
                }
                String url = pattern.replaceAll("\\{[^}]*}", "1");
                int status = mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), url))
                        .andReturn().getResponse().getStatus();
                checked++;
                if (status != 401) {
                    notProtected.add(key + " -> " + status);
                }
            }
        }

        assertThat(notProtected.isEmpty()).as("로그인 없이 401이 아닌 API: " + notProtected).isTrue();
        assertThat(checked > 40).as("검사한 API 수가 너무 적다: " + checked).isTrue();
        assertThat(seenPublic).as("공개 목록에 있는데 실제로 없는 API(오래된 항목)").isEqualTo(PUBLIC);
    }
}