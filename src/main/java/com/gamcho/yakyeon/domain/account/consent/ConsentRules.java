package com.gamcho.yakyeon.domain.account.consent;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 약관 동의 규칙 (스프링·DB 없는 순수 로직).
 *
 * 동의 한 건은 user_agreement의 한 행(계정 x 약관 버전)이다. 철회하면 행을 지우지 않고 withdrawn_at만 채운다
 * (DB 제약상 철회한 행도 agreed는 true로 남는다). 그래서 "지금 유효한 동의"는 agreed이고 withdrawn_at이 비어 있는 행이다.
 * 이력은 agreement_log에 남고 수정·삭제할 수 없다.
 */
public final class ConsentRules {

    public static final String TOS = "TOS";
    public static final String PRIVACY = "PRIVACY";
    public static final String SENSITIVE_HEALTH = "SENSITIVE_HEALTH";
    public static final String SERVICE_NOTICE = "SERVICE_NOTICE";
    public static final String MEDICATION = "MEDICATION";
    public static final String DEVICE = "DEVICE";

    /** 서비스를 쓰는 데 항상 필요한 동의. 하나라도 철회하면 건강정보 API를 쓸 수 없다 */
    public static final List<String> SERVICE_REQUIRED = List.of(TOS, PRIVACY, SENSITIVE_HEALTH);

    /** 앱의 "서비스 안내 및 정보 동의"에서 다루는 약관 종류. 응답도 이 순서로 준다 */
    public static final List<String> AGREEABLE = List.of(TOS, PRIVACY, SENSITIVE_HEALTH, SERVICE_NOTICE, MEDICATION, DEVICE);

    /** 철회할 수 있는 종류. 서비스 이용 고지(SERVICE_NOTICE)는 확인 체크뿐이라 철회할 수 없다 */
    public static final List<String> WITHDRAWABLE = List.of(TOS, PRIVACY, SENSITIVE_HEALTH, MEDICATION, DEVICE);

    private ConsentRules() {
    }

    /** user_agreement 한 행 (약관 종류와 버전 이름을 붙여서) */
    public record Row(String termsType, long termsVersionId, String version, boolean agreed,
                      OffsetDateTime agreedAt, OffsetDateTime withdrawnAt) {

        /** 지금 유효한 동의인가 (동의했고 철회하지 않았다) */
        public boolean isActive() {
            return agreed && withdrawnAt == null;
        }

        /** 마지막으로 바뀐 시각: 철회했으면 철회 시각, 아니면 동의 시각 */
        public OffsetDateTime changedAt() {
            return withdrawnAt != null ? withdrawnAt : agreedAt;
        }
    }

    /** 앱에 보여 주는 약관 종류별 현재 상태 */
    public record State(String termsType, long termsVersionId, String version, boolean agreed, OffsetDateTime changedAt) {
    }

    public static boolean isAgreeable(String termsType) {
        return termsType != null && AGREEABLE.contains(termsType);
    }

    public static boolean isWithdrawable(String termsType) {
        return termsType != null && WITHDRAWABLE.contains(termsType);
    }

    /**
     * 약관 종류마다 가장 최근에 동의한 행(같은 시각이면 버전 번호가 큰 것) 하나를 골라 현재 상태로 바꾼다.
     * 한 번도 동의하지 않은 종류와 이 화면에서 다루지 않는 종류(위임 동의 등)는 뺀다. 순서는 AGREEABLE과 같다.
     */
    public static List<State> currentStates(Collection<Row> rows) {
        Map<String, Row> latest = new HashMap<>();
        for (Row row : rows) {
            if (isAgreeable(row.termsType())) {
                latest.merge(row.termsType(), row, ConsentRules::newer);
            }
        }
        List<State> result = new ArrayList<>();
        for (String type : AGREEABLE) {
            Row row = latest.get(type);
            if (row != null) {
                result.add(new State(type, row.termsVersionId(), row.version(), row.isActive(), row.changedAt()));
            }
        }
        return result;
    }

    private static Row newer(Row a, Row b) {
        int byTime = a.agreedAt().compareTo(b.agreedAt());
        if (byTime != 0) {
            return byTime > 0 ? a : b;
        }
        return a.termsVersionId() >= b.termsVersionId() ? a : b;
    }
}