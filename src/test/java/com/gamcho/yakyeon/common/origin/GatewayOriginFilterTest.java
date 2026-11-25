package com.gamcho.yakyeon.common.origin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** API Gateway를 거친 요청만 받는 필터. 스프링 컨텍스트 없이 필터와 샘플 컨트롤러만 붙여서 확인한다. */
class GatewayOriginFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OUTSIDER = "198.51.100.9";   // 서버에 직접 연결한 바깥 주소
    private static final String GATEWAY = "10.0.0.5";

    private static MockMvc mockMvc(boolean enabled, boolean allowLoopback) {
        return MockMvcBuilders.standaloneSetup(new SampleController())
                .addFilters(new GatewayOriginFilter(enabled, SECRET, allowLoopback, 1))
                .build();
    }

    private static MvcResult call(MockMvc mockMvc, String remote, String originHeader, String forwardedFor) throws Exception {
        MockHttpServletRequestBuilder request = get("/open");
        if (originHeader != null) {
            request.header("X-Origin-Verify", originHeader);
        }
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return mockMvc.perform(request.with(r -> {
            r.setRemoteAddr(remote);
            return r;
        })).andReturn();
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    @Test
    @DisplayName("꺼져 있으면(기본) 헤더가 없어도 모두 통과한다 (코드만 배포해서는 동작이 바뀌지 않는다)")
    void disabledAllowsEverything() throws Exception {
        assertThat(status(call(mockMvc(false, true), OUTSIDER, null, null))).isEqualTo(200);
    }

    @Test
    @DisplayName("켜져 있고 출처 헤더가 없으면 403 FORBIDDEN_ORIGIN이고 요청은 컨트롤러에 가지 않는다")
    void blocksDirectCalls() throws Exception {
        MvcResult blocked = call(mockMvc(true, true), OUTSIDER, null, null);
        assertThat(status(blocked)).isEqualTo(403);
        String body = blocked.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body.contains("\"code\":\"FORBIDDEN_ORIGIN\"")).isTrue();
        assertThat(body.contains("ok")).as("컨트롤러의 응답이 섞이지 않는다").isFalse();
    }

    @Test
    @DisplayName("출처 헤더가 틀리면 403이다")
    void blocksWrongHeader() throws Exception {
        assertThat(status(call(mockMvc(true, true), OUTSIDER, "wrong", null))).isEqualTo(403);
        assertThat(status(call(mockMvc(true, true), OUTSIDER, SECRET + "x", null))).isEqualTo(403);
    }

    @Test
    @DisplayName("출처 헤더가 맞으면 통과한다 (API Gateway를 거친 요청)")
    void allowsGatewayCalls() throws Exception {
        assertThat(status(call(mockMvc(true, true), GATEWAY, SECRET, "203.0.113.7"))).isEqualTo(200);
    }

    @Test
    @DisplayName("같은 서버 안(127.0.0.1)에서 온 요청은 헤더 없이도 통과한다 (서버에서 직접 점검)")
    void allowsLoopback() throws Exception {
        assertThat(status(call(mockMvc(true, true), "127.0.0.1", null, null))).isEqualTo(200);
    }

    @Test
    @DisplayName("루프백 허용을 끄면 같은 서버에서 온 요청도 막는다")
    void loopbackCanBeTurnedOff() throws Exception {
        assertThat(status(call(mockMvc(true, false), "127.0.0.1", null, null))).isEqualTo(403);
    }

    @Test
    @DisplayName("켰는데 비밀 값이 없거나 짧으면 서버(필터)가 만들어지지 않는다")
    void refusesToStartWithABadSecret() {
        for (String bad : new String[] {"", "short"}) {
            try {
                new GatewayOriginFilter(true, bad, true, 1);
                assertThat(false).as("예외가 나야 한다: " + bad).isTrue();
            } catch (IllegalStateException e) {
                assertThat(e.getMessage().contains("GATEWAY_ORIGIN_SECRET")).isTrue();
                assertThat(bad.isEmpty() || !e.getMessage().contains(bad)).as("메시지에 비밀 값이 없다").isTrue();
            }
        }
    }

    @RestController
    static class SampleController {

        @GetMapping("/open")
        String open() {
            return "ok";
        }
    }
}