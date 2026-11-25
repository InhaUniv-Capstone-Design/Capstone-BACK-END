package com.gamcho.yakyeon.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.gamcho.yakyeon.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 접속 주소별 호출 제한. 스프링 컨텍스트 없이 인터셉터와 오류 핸들러만 붙여서 확인한다. */
class RateLimitInterceptorTest {

    private static MockMvc mockMvc(boolean enabled, AtomicLong clock) {
        return MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RateLimitInterceptor(enabled, clock::get))
                .build();
    }

    private static MvcResult call(MockMvc mockMvc, String address, String path) throws Exception {
        return mockMvc.perform(get(path).with(request -> {
            request.setRemoteAddr(address);
            return request;
        })).andReturn();
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    @Test
    @DisplayName("한도(2번)까지는 통과하고 넘으면 429 RATE_LIMITED")
    void blocksAfterTheLimit() throws Exception {
        MockMvc mockMvc = mockMvc(true, new AtomicLong(1_000_000L));

        assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(200);
        assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(200);
        MvcResult blocked = call(mockMvc, "1.1.1.1", "/limited");

        assertThat(status(blocked)).isEqualTo(429);
        assertThat(blocked.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("\"code\":\"RATE_LIMITED\"")).isTrue();
    }

    @Test
    @DisplayName("접속 주소마다 따로 센다")
    void countsPerAddress() throws Exception {
        MockMvc mockMvc = mockMvc(true, new AtomicLong(1_000_000L));
        call(mockMvc, "1.1.1.1", "/limited");
        call(mockMvc, "1.1.1.1", "/limited");

        assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(429);
        assertThat(status(call(mockMvc, "2.2.2.2", "/limited"))).isEqualTo(200);
    }

    @Test
    @DisplayName("요청 헤더 X-Forwarded-For를 바꿔 보내도 한도를 피할 수 없다 (위조할 수 있는 값은 믿지 않는다)")
    void forwardedHeaderCannotEvadeTheLimit() throws Exception {
        MockMvc mockMvc = mockMvc(true, new AtomicLong(1_000_000L));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/limited").header("X-Forwarded-For", "9.9.9." + i).with(request -> {
                request.setRemoteAddr("1.1.1.1");
                return request;
            }));
        }
        MvcResult third = mockMvc.perform(get("/limited").header("X-Forwarded-For", "9.9.9.99").with(request -> {
            request.setRemoteAddr("1.1.1.1");
            return request;
        })).andReturn();

        assertThat(status(third)).isEqualTo(429);
    }

    @Test
    @DisplayName("창이 끝나면(60초 뒤) 다시 쓸 수 있다")
    void windowResets() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000L);
        MockMvc mockMvc = mockMvc(true, clock);
        call(mockMvc, "1.1.1.1", "/limited");
        call(mockMvc, "1.1.1.1", "/limited");
        assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(429);

        clock.addAndGet(60);

        assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(200);
    }

    @Test
    @DisplayName("@RateLimit이 없는 API는 제한하지 않고, 이름이 다른 제한은 따로 센다")
    void onlyAnnotatedEndpointsAndSeparateNames() throws Exception {
        MockMvc mockMvc = mockMvc(true, new AtomicLong(1_000_000L));
        for (int i = 0; i < 10; i++) {
            assertThat(status(call(mockMvc, "1.1.1.1", "/free"))).isEqualTo(200);
        }
        call(mockMvc, "1.1.1.1", "/limited");
        call(mockMvc, "1.1.1.1", "/limited");
        assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(429);
        assertThat(status(call(mockMvc, "1.1.1.1", "/other"))).as("다른 이름의 한도는 따로").isEqualTo(200);
        assertThat(status(call(mockMvc, "1.1.1.1", "/other"))).isEqualTo(429);
    }

    @Test
    @DisplayName("rate-limit.enabled=false면 제한하지 않는다 (시험용)")
    void canBeDisabled() throws Exception {
        MockMvc mockMvc = mockMvc(false, new AtomicLong(1_000_000L));
        for (int i = 0; i < 10; i++) {
            assertThat(status(call(mockMvc, "1.1.1.1", "/limited"))).isEqualTo(200);
        }
    }

    // ---- 테스트용 컨트롤러

    @RestController
    static class SampleController {

        @GetMapping("/limited")
        @RateLimit(name = "sample", limit = 2, windowSeconds = 60)
        String limited() {
            return "ok";
        }

        @GetMapping("/other")
        @RateLimit(name = "other", limit = 1, windowSeconds = 60)
        String other() {
            return "ok";
        }

        @GetMapping("/free")
        String free() {
            return "ok";
        }
    }
}