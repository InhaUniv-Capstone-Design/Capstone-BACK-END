package com.gamcho.yakyeon.common.startup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실제 서버(내 컴퓨터가 아닌 곳의 DB를 쓰는 서버)가 위험한 설정으로 시작하지 못하게 하는 규칙 (스프링 없는 순수 로직).
 * 개발 편의로 저장소에 있는 기본값이 실제 서버에 그대로 쓰이는 사고를 막으려는 것이다.
 *
 *  1) DB 비밀번호가 비어 있거나 개발용 기본값(postgres)이면 시작하지 않는다.
 *  2) 문자 인증이 개발용(mock)이면 시작하지 않는다 (인증번호가 서버 로그에 평문으로 남는다).
 *     시험 서버라서 일부러 쓰는 경우에는 APP_ALLOW_MOCK_SMS=true로 명시적으로 허용해야 한다.
 *
 * "내 컴퓨터"는 DB 주소가 localhost, 127.0.0.1, ::1, host.docker.internal 인 경우다. 주소를 읽을 수 없으면 실제 서버로 본다.
 */
public final class StartupSafetyRules {

    private static final Pattern JDBC_HOST = Pattern.compile("^jdbc:postgresql://(\\[[^\\]]+\\]|[^:/?#]+)");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0", "host.docker.internal");
    private static final String DEV_DB_PASSWORD = "postgres";

    private StartupSafetyRules() {
    }

    /** jdbc:postgresql://호스트:포트/DB 에서 호스트를 꺼낸다. 읽을 수 없으면 null */
    public static String databaseHost(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        Matcher m = JDBC_HOST.matcher(jdbcUrl.trim());
        return m.find() ? m.group(1) : null;
    }

    /** 이 호스트가 "내 컴퓨터"인가. null(읽을 수 없음)은 아니다 */
    public static boolean isLocal(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return LOCAL_HOSTS.contains(h) || h.endsWith(".localhost");
    }

    /** 시작을 막아야 하는 문제들(사람이 읽는 문장, 비밀 값은 담지 않는다). 없으면 빈 목록 */
    public static List<String> problems(String jdbcUrl, String dbPassword, String phoneVerificationProvider,
                                        boolean allowMockSms) {
        List<String> problems = new ArrayList<>();
        if (isLocal(databaseHost(jdbcUrl))) {
            return problems;   // 개발 PC는 편의 기본값을 그대로 쓴다
        }
        if (dbPassword == null || dbPassword.isBlank() || DEV_DB_PASSWORD.equals(dbPassword)) {
            problems.add("실제 서버인데 DB 비밀번호가 비어 있거나 개발용 기본값입니다. "
                    + "SPRING_DATASOURCE_PASSWORD 환경변수에 별도의 비밀번호를 설정하세요.");
        }
        boolean octomo = phoneVerificationProvider != null && "octomo".equalsIgnoreCase(phoneVerificationProvider.trim());
        if (!octomo && !allowMockSms) {
            problems.add("실제 서버인데 문자 인증이 개발용(mock)입니다. 인증번호가 서버 로그에 남습니다. "
                    + "PHONE_VERIFICATION_PROVIDER=octomo와 OCTOMO_API_KEY를 설정하세요. "
                    + "시험 서버라서 일부러 쓰는 것이라면 APP_ALLOW_MOCK_SMS=true로 허용하세요.");
        }
        return problems;
    }
}