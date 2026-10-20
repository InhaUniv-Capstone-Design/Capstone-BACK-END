package com.gamcho.yakyeon.domain.interaction.logic;

/** 화면에 나가는 고정 문구와 표시용 변환. 법률 검토 대상 문구라 한 곳에 모아 둔다. */
public final class InteractionTexts {

    /** 모든 병용금기 상세에 붙는 면책 문구 (진단이 아니라는 점과 의료진 상담) */
    public static final String DISCLAIMER = "본 정보는 진단이 아니며 최종 판단은 의료진과 상담하세요.";

    /** 병용 시 주의사항 */
    public static final String PRECAUTION = "복용 중인 조합을 임의로 바꾸지 말고 의료진과 상담하세요.";

    /** DUR 데이터 적재 도구가 사유가 비어 있는 규칙에 넣어 두는 값 */
    private static final String LOADER_PLACEHOLDER = "(사유 미기재)";

    private static final String NO_REASON = "DUR에 사유가 등재되어 있지 않습니다. 의료진과 상담해 주세요.";

    private InteractionTexts() {
    }

    /** 사유가 없거나 적재 도구의 자리표시자면 사용자에게 보여줄 문구로 바꾼다 */
    public static String reason(String raw) {
        if (raw == null || raw.isBlank() || LOADER_PLACEHOLDER.equals(raw.trim())) {
            return NO_REASON;
        }
        return raw.trim();
    }

    /** DB의 출처 이름을 화면용 이름으로 바꾼다. 모르는 값은 그대로 보여준다. */
    public static String source(String raw) {
        if (raw == null || raw.isBlank() || "MFDS DUR".equals(raw.trim())) {
            return "식품의약품안전처 DUR";
        }
        return raw.trim();
    }
}