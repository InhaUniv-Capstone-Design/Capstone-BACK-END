package com.gamcho.yakyeon.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스프링 컨텍스트나 DB 없이 핸들러만 확인하는 테스트(standalone MockMvc).
 * 응답은 { "code": "...", "message": "..." } 한 가지 모양이어야 한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MvcResult run(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("검증 오류의 필드 이름은 snake_case로 나온다")
    void validationErrorUsesSnakeCaseFieldName() throws Exception {
        MvcResult result = run(put("/sample/token").contentType(MediaType.APPLICATION_JSON).content("{}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(bodyOf(result)).contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(bodyOf(result)).contains("fcm_token: ");
        assertThat(bodyOf(result)).doesNotContain("fcmToken");
    }

    @Test
    @DisplayName("존재하지 않는 주소는 500이 아니라 404 NOT_FOUND")
    void unknownPathIs404() throws Exception {
        MvcResult result = run(get("/no/such/path"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(bodyOf(result)).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    @DisplayName("허용되지 않는 메서드는 405 METHOD_NOT_ALLOWED이고 Allow 헤더가 남는다")
    void wrongMethodIs405() throws Exception {
        MvcResult result = run(post("/sample/only-get"));

        assertThat(result.getResponse().getStatus()).isEqualTo(405);
        assertThat(bodyOf(result)).contains("\"code\":\"METHOD_NOT_ALLOWED\"");
        assertThat(result.getResponse().getHeader("Allow")).isNotNull();
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMediaTypeIs415() throws Exception {
        MvcResult result = run(put("/sample/token").contentType(MediaType.TEXT_PLAIN).content("x"));

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(bodyOf(result)).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\"");
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR이고 내부 내용을 응답에 싣지 않는다")
    void unexpectedExceptionIs500WithoutDetails() throws Exception {
        MvcResult result = run(get("/sample/boom"));

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(bodyOf(result)).contains("\"code\":\"INTERNAL_ERROR\"");
        assertThat(bodyOf(result)).doesNotContain("secret internal detail");
        assertThat(bodyOf(result)).doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("업무 예외는 그대로 자기 상태 코드와 코드 이름으로 나간다")
    void businessExceptionKeepsItsOwnCode() throws Exception {
        MvcResult result = run(get("/sample/business"));

        assertThat(result.getResponse().getStatus()).isEqualTo(ErrorCode.USER_NOT_FOUND.getStatus().value());
        assertThat(bodyOf(result)).contains("\"code\":\"USER_NOT_FOUND\"");
    }

    @Test
    @DisplayName("스프링 시큐리티 예외는 500으로 바뀌지 않고 그대로 다시 던져진다")
    void securityExceptionIsRethrown() {
        assertThatThrownBy(() -> mockMvc.perform(get("/sample/denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    // ---- 테스트용 컨트롤러 ----

    @RestController
    static class SampleController {

        @PutMapping("/sample/token")
        void putToken(@Valid @RequestBody TokenRequest request) {
        }

        @GetMapping("/sample/only-get")
        String onlyGet() {
            return "ok";
        }

        @GetMapping("/sample/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/sample/business")
        String business() {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        @GetMapping("/sample/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }
    }

    static class TokenRequest {

        @JsonProperty("fcm_token")
        @NotBlank(message = "푸시 토큰을 입력해주세요.")
        private String fcmToken;
    }
}