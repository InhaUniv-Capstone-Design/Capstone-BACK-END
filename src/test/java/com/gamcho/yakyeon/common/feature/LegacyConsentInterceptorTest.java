package com.gamcho.yakyeon.common.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 옛 문자 인증 방식 차단 확인. 스프링 컨텍스트와 DB 없이 인터셉터와 오류 핸들러만 붙여서 본다.
 */
class LegacyConsentInterceptorTest {

    private static MockMvc mockMvc(boolean legacyEnabled) {
        LegacyConsentFeature feature = new LegacyConsentFeature(legacyEnabled);
        return MockMvcBuilders.standaloneSetup(new SampleController(), new WholeLegacyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new LegacyConsentInterceptor(feature))
                .build();
    }

    private static MvcResult run(MockMvc mockMvc, RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("꺼져 있으면(기본) 표시된 메서드는 404 FEATURE_DISABLED")
    void disabledMethodReturns404() throws Exception {
        MvcResult result = run(mockMvc(false), get("/sample/legacy"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(bodyOf(result)).contains("\"code\":\"FEATURE_DISABLED\"");
    }

    @Test
    @DisplayName("꺼져 있으면 클래스 전체에 표시된 컨트롤러도 404")
    void disabledWholeControllerReturns404() throws Exception {
        MvcResult result = run(mockMvc(false), post("/sample/whole"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(bodyOf(result)).contains("\"code\":\"FEATURE_DISABLED\"");
    }

    @Test
    @DisplayName("표시가 없는 정상 API는 꺼져 있어도 영향이 없다")
    void normalEndpointIsNotAffected() throws Exception {
        MvcResult result = run(mockMvc(false), get("/sample/normal"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(result)).isEqualTo("normal");
    }

    @Test
    @DisplayName("켜면 표시된 메서드와 클래스가 다시 동작한다")
    void enabledAllowsLegacy() throws Exception {
        MockMvc enabled = mockMvc(true);

        assertThat(run(enabled, get("/sample/legacy")).getResponse().getStatus()).isEqualTo(200);
        assertThat(run(enabled, post("/sample/whole")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("서비스 안에서 쓰는 requireEnabled는 꺼져 있으면 FEATURE_DISABLED를 던진다")
    void requireEnabledThrowsWhenDisabled() {
        assertThatThrownBy(() -> new LegacyConsentFeature(false).requireEnabled())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FEATURE_DISABLED));
        new LegacyConsentFeature(true).requireEnabled(); // 켜져 있으면 아무 일도 없다
    }

    // ---- 테스트용 컨트롤러 ----

    @RestController
    static class SampleController {

        @GetMapping("/sample/legacy")
        @LegacyConsentOnly
        String legacy() {
            return "legacy";
        }

        @GetMapping("/sample/normal")
        String normal() {
            return "normal";
        }
    }

    @RestController
    @LegacyConsentOnly
    static class WholeLegacyController {

        @PostMapping("/sample/whole")
        String whole() {
            return "whole";
        }
    }
}