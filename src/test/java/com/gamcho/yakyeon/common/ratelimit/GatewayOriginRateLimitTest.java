package com.gamcho.yakyeon.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.gamcho.yakyeon.common.exception.GlobalExceptionHandler;
import com.gamcho.yakyeon.common.origin.GatewayOriginFilter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * gateway-origin을 켠 서버에서 호출 제한이 API Gateway의 주소가 아니라 사용자별 주소로 세는지 확인한다.
 * (호출 제한 인터셉터의 시계 생성자가 같은 패키지에만 열려 있어서 이 패키지에 둔다)
 */
class GatewayOriginRateLimitTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String GATEWAY = "10.0.0.5";   // 모든 사용자가 이 주소로 연결한 것으로 보인다

    private static MockMvc mockMvc(boolean originEnabled) {
        return MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new GatewayOriginFilter(originEnabled, SECRET, true, 1))
                .addInterceptors(new RateLimitInterceptor(true, new AtomicLong(1_000_000L)::get))
                .build();
    }

    private static int call(MockMvc mockMvc, String originHeader, String forwardedFor) throws Exception {
        MockHttpServletRequestBuilder request = get("/limited");
        if (originHeader != null) {
            request.header("X-Origin-Verify", originHeader);
        }
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        MvcResult result = mockMvc.perform(request.with(r -> {
            r.setRemoteAddr(GATEWAY);
            return r;
        })).andReturn();
        return result.getResponse().getStatus();
    }

    @Test
    @DisplayName("켜면 사용자마다 따로 센다: 같은 API Gateway 주소로 연결돼도 한 사람이 한도를 넘어도 다른 사람은 쓸 수 있다")
    void countsPerRealClient() throws Exception {
        MockMvc mockMvc = mockMvc(true);
        assertThat(call(mockMvc, SECRET, "203.0.113.1")).isEqualTo(200);
        assertThat(call(mockMvc, SECRET, "203.0.113.1")).isEqualTo(200);
        assertThat(call(mockMvc, SECRET, "203.0.113.1")).as("1번 사용자는 한도 초과").isEqualTo(429);
        assertThat(call(mockMvc, SECRET, "203.0.113.2")).as("2번 사용자는 영향 없음").isEqualTo(200);
    }

    @Test
    @DisplayName("앞에 위조한 주소를 붙여 보내도 한도를 피할 수 없다 (맨 오른쪽 진짜 주소로 센다)")
    void forgedLeadingAddressesCannotEvadeTheLimit() throws Exception {
        MockMvc mockMvc = mockMvc(true);
        assertThat(call(mockMvc, SECRET, "1.1.1.1, 203.0.113.1")).isEqualTo(200);
        assertThat(call(mockMvc, SECRET, "2.2.2.2, 203.0.113.1")).isEqualTo(200);
        assertThat(call(mockMvc, SECRET, "3.3.3.3, 203.0.113.1")).as("같은 사용자라서 세 번째는 한도 초과").isEqualTo(429);
    }

    @Test
    @DisplayName("끄면(기본) 지금까지와 같다: X-Forwarded-For를 믿지 않고 연결된 주소로 센다")
    void disabledKeepsTheOldBehavior() throws Exception {
        MockMvc mockMvc = mockMvc(false);
        assertThat(call(mockMvc, null, "203.0.113.1")).isEqualTo(200);
        assertThat(call(mockMvc, null, "203.0.113.2")).isEqualTo(200);
        assertThat(call(mockMvc, null, "203.0.113.3")).as("모두 같은 연결 주소라서 한도를 함께 쓴다").isEqualTo(429);
    }

    @Test
    @DisplayName("출처 헤더가 틀린 요청은 한도를 세기 전에 거절된다 (위조한 주소로 다른 사람의 한도를 쓰게 만들 수 없다)")
    void blockedRequestsNeverTouchTheCounter() throws Exception {
        MockMvc mockMvc = mockMvc(true);
        for (int i = 0; i < 5; i++) {
            assertThat(call(mockMvc, "wrong", "203.0.113.1")).isEqualTo(403);
        }
        assertThat(call(mockMvc, SECRET, "203.0.113.1")).as("거절된 요청은 1번 사용자의 한도에 포함되지 않는다").isEqualTo(200);
    }

    @RestController
    static class SampleController {

        @GetMapping("/limited")
        @RateLimit(name = "sample", limit = 2, windowSeconds = 60)
        String limited() {
            return "ok";
        }
    }
}