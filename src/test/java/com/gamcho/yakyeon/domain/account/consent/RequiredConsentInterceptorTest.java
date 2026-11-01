package com.gamcho.yakyeon.domain.account.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.gamcho.yakyeon.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 필수 동의(이용약관·개인정보·건강정보)를 철회한 사용자의 호출 차단 확인.
 * 스프링 컨텍스트와 DB 없이 인터셉터와 오류 핸들러만 붙이고, 동의 조회는 람다로 대신한다.
 */
class RequiredConsentInterceptorTest {

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static MockMvc mockMvc(ActiveConsentLookup lookup) {
        return MockMvcBuilders.standaloneSetup(new SampleController(), new ExemptController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RequiredConsentInterceptor(lookup))
                .build();
    }

    private static MvcResult run(MockMvc mockMvc, RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("필수 동의가 모두 있으면 통과한다")
    void passesWhenAllConsentsActive() throws Exception {
        loginAs(7L);
        MvcResult result = run(mockMvc((userId, types) -> true), get("/sample/health"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(result)).isEqualTo("health");
    }

    @Test
    @DisplayName("필수 동의가 철회돼 있으면 403 CONSENT_REQUIRED")
    void blockedWhenConsentWithdrawn() throws Exception {
        loginAs(7L);
        MvcResult result = run(mockMvc((userId, types) -> false), get("/sample/health"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(bodyOf(result)).contains("\"code\":\"CONSENT_REQUIRED\"");
    }

    @Test
    @DisplayName("@ConsentExempt가 붙은 API는 동의가 없어도 쓸 수 있다")
    void exemptEndpointIsNotBlocked() throws Exception {
        loginAs(7L);
        MockMvc blocked = mockMvc((userId, types) -> false);

        assertThat(run(blocked, get("/exempt/method")).getResponse().getStatus()).isEqualTo(200);
        assertThat(run(blocked, get("/exempt/class")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 이 인터셉터가 막지 않는다(보안 설정이 따로 처리한다)")
    void anonymousIsNotChecked() throws Exception {
        MvcResult result = run(mockMvc((userId, types) -> false), get("/sample/health"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("확인하는 약관은 이용약관, 개인정보, 건강정보이고 로그인한 사용자 번호로 묻는다")
    void asksForTheThreeRequiredTypes() throws Exception {
        loginAs(42L);
        List<Long> askedUsers = new ArrayList<>();
        List<Collection<String>> askedTypes = new ArrayList<>();
        MockMvc mockMvc = mockMvc((userId, types) -> {
            askedUsers.add(userId);
            askedTypes.add(types);
            return true;
        });

        run(mockMvc, get("/sample/health"));

        assertThat(askedUsers.size()).isEqualTo(1);
        assertThat(askedUsers.get(0)).isEqualTo(42L);
        assertThat(askedTypes.get(0).contains("TOS")).isTrue();
        assertThat(askedTypes.get(0).contains("PRIVACY")).isTrue();
        assertThat(askedTypes.get(0).contains("SENSITIVE_HEALTH")).isTrue();
        assertThat(askedTypes.get(0).size()).isEqualTo(3);
    }

    // ---- 테스트용 컨트롤러 ----

    @RestController
    static class SampleController {

        @GetMapping("/sample/health")
        String health() {
            return "health";
        }

        @GetMapping("/exempt/method")
        @ConsentExempt
        String exemptMethod() {
            return "ok";
        }
    }

    @RestController
    @ConsentExempt
    static class ExemptController {

        @GetMapping("/exempt/class")
        String exemptClass() {
            return "ok";
        }
    }
}