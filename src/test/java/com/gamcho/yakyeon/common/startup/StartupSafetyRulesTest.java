package com.gamcho.yakyeon.common.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StartupSafetyRulesTest {

    private static final String LOCAL_URL = "jdbc:postgresql://localhost:5432/yakyeon";
    private static final String RDS_URL = "jdbc:postgresql://yakyeon-db.abc123.ap-northeast-2.rds.amazonaws.com:5432/yakyeon";

    private static List<String> problems(String url, String password, String provider, boolean allowMock) {
        return StartupSafetyRules.problems(url, password, provider, allowMock);
    }

    @Test
    @DisplayName("DB 주소에서 호스트를 꺼낸다 (포트, 옵션, IPv6 포함)")
    void parsesHost() {
        assertThat(StartupSafetyRules.databaseHost(LOCAL_URL)).isEqualTo("localhost");
        assertThat(StartupSafetyRules.databaseHost(RDS_URL)).isEqualTo("yakyeon-db.abc123.ap-northeast-2.rds.amazonaws.com");
        assertThat(StartupSafetyRules.databaseHost("jdbc:postgresql://10.0.1.5/yakyeon?sslmode=require")).isEqualTo("10.0.1.5");
        assertThat(StartupSafetyRules.databaseHost("jdbc:postgresql://[::1]:5432/yakyeon")).isEqualTo("[::1]");
        assertThat(StartupSafetyRules.databaseHost("jdbc:mysql://x/y") == null).isTrue();
        assertThat(StartupSafetyRules.databaseHost("") == null).isTrue();
        assertThat(StartupSafetyRules.databaseHost(null) == null).isTrue();
    }

    @Test
    @DisplayName("내 컴퓨터 주소만 로컬이다 (비슷해 보이는 주소는 로컬이 아니다)")
    void localHosts() {
        for (String h : new String[]{"localhost", "LOCALHOST", "127.0.0.1", "::1", "[::1]", "host.docker.internal", "db.localhost"}) {
            assertThat(StartupSafetyRules.isLocal(h)).as(h).isTrue();
        }
        for (String h : new String[]{"localhost.evil.com", "127.0.0.2", "10.0.0.5", "mydb.rds.amazonaws.com", "", "notlocalhost"}) {
            assertThat(StartupSafetyRules.isLocal(h)).as(h).isFalse();
        }
        assertThat(StartupSafetyRules.isLocal(null)).as("읽을 수 없는 주소는 로컬이 아니다").isFalse();
    }

    @Test
    @DisplayName("개발 PC(로컬 DB)는 기본 비밀번호와 mock 문자 인증을 그대로 쓸 수 있다")
    void developerMachineIsUnrestricted() {
        assertThat(problems(LOCAL_URL, "postgres", "mock", false).isEmpty()).isTrue();
        assertThat(problems(LOCAL_URL, "", "mock", false).isEmpty()).isTrue();
        assertThat(problems("jdbc:postgresql://127.0.0.1:5432/yakyeon", "postgres", "mock", false).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("실제 서버가 개발용 DB 비밀번호(postgres)나 빈 비밀번호면 시작하지 않는다")
    void rejectsDevDatabasePassword() {
        assertThat(problems(RDS_URL, "postgres", "octomo", false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, "", "octomo", false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, "   ", "octomo", false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, null, "octomo", false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, "postgres", "octomo", false).get(0).contains("SPRING_DATASOURCE_PASSWORD")).isTrue();
    }

    @Test
    @DisplayName("실제 서버가 mock 문자 인증이면 시작하지 않는다 (인증번호가 로그에 남는다)")
    void rejectsMockSmsOnRealServer() {
        assertThat(problems(RDS_URL, "S3cure-Pw!", "mock", false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, "S3cure-Pw!", "", false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, "S3cure-Pw!", null, false).size()).isEqualTo(1);
        assertThat(problems(RDS_URL, "S3cure-Pw!", "mock", false).get(0).contains("PHONE_VERIFICATION_PROVIDER")).isTrue();
    }

    @Test
    @DisplayName("시험 서버가 일부러 mock을 쓰려면 명시적으로 허용해야 한다 (DB 비밀번호 규칙은 그대로 적용)")
    void mockCanBeAllowedExplicitly() {
        assertThat(problems(RDS_URL, "S3cure-Pw!", "mock", true).isEmpty()).isTrue();
        assertThat(problems(RDS_URL, "postgres", "mock", true).size()).as("허용해도 약한 DB 비밀번호는 막는다").isEqualTo(1);
    }

    @Test
    @DisplayName("제대로 설정된 실제 서버는 통과한다 (octomo는 대소문자와 공백을 무시)")
    void correctlyConfiguredRealServerPasses() {
        assertThat(problems(RDS_URL, "S3cure-Pw!", "octomo", false).isEmpty()).isTrue();
        assertThat(problems(RDS_URL, "S3cure-Pw!", " OCTOMO ", false).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("둘 다 잘못되면 문제를 모두 알려 준다 (하나씩 고쳐서 다시 켜는 번거로움을 줄인다)")
    void reportsEveryProblem() {
        assertThat(problems(RDS_URL, "postgres", "mock", false).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("주소를 읽을 수 없으면 실제 서버로 본다 (개발용 기본값을 그대로 쓰는 일을 막는다)")
    void unreadableUrlIsTreatedAsRealServer() {
        assertThat(problems("weird-url", "postgres", "mock", false).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("오류 문장에 비밀번호 값이 들어가지 않는다")
    void messagesNeverContainTheSecret() {
        for (String message : problems(RDS_URL, "postgres", "mock", false)) {
            assertThat(message.contains("postgres")).as("문장에 비밀번호 값(postgres)이 있으면 안 됨: " + message).isFalse();
        }
        for (String message : problems(RDS_URL, "MySecret123", "mock", false)) {
            assertThat(message.contains("MySecret123")).isFalse();
        }
    }
}