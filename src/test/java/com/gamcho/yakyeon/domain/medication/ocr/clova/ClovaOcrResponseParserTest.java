package com.gamcho.yakyeon.domain.medication.ocr.clova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.OcrUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClovaOcrResponseParserTest {

    private static String reasonOf(String json) {
        try {
            ClovaOcrResponseParser.parse(json);
        } catch (OcrUnavailableException e) {
            return e.getReason();
        }
        return null;
    }

    /** 네이버 문서(General OCR, V2)의 응답 예시와 같은 모양 */
    private static final String DOC_EXAMPLE = "{\"version\":\"V2\",\"requestId\":\"x\",\"timestamp\":1576569034247,\"images\":[{\"uid\":\"u\","
            + "\"name\":\"medium\",\"inferResult\":\"SUCCESS\",\"message\":\"SUCCESS\",\"validationResult\":{\"result\":\"NO_REQUESTED\"},"
            + "\"fields\":["
            + "{\"valueType\":\"ALL\",\"inferText\":\"아름다운\",\"inferConfidence\":0.99992156,\"type\":\"NORMAL\",\"lineBreak\":true,"
            + "\"boundingPoly\":{\"vertices\":[{\"x\":2713.7295,\"y\":1277.0492},{\"x\":2713.7295,\"y\":977.7408},{\"x\":2841.4343,\"y\":977.7408},{\"x\":2841.4343,\"y\":1277.0492}]}},"
            + "{\"valueType\":\"ALL\",\"inferText\":\"이\",\"inferConfidence\":0.99958915,\"type\":\"NORMAL\",\"lineBreak\":false,"
            + "\"boundingPoly\":{\"vertices\":[{\"x\":2314.6516,\"y\":1468.6066},{\"x\":2314.6516,\"y\":1328.9293},{\"x\":2426.3936,\"y\":1328.9293},{\"x\":2426.3936,\"y\":1468.6066}]}},"
            + "{\"valueType\":\"ALL\",\"inferText\":\"세상\",\"inferConfidence\":0.9998707,\"type\":\"NORMAL\",\"lineBreak\":false,"
            + "\"boundingPoly\":{\"vertices\":[{\"x\":2314.6516,\"y\":1604.2931},{\"x\":2314.6516,\"y\":1460.625},{\"x\":2430.3843,\"y\":1460.625},{\"x\":2430.3843,\"y\":1604.2931}]}}"
            + "]}]}";

    private static String field(String text, double x, double y, Boolean lineBreak) {
        String lb = lineBreak == null ? "" : ",\"lineBreak\":" + lineBreak;
        return "{\"inferText\":\"" + text + "\",\"inferConfidence\":0.98" + lb + ",\"boundingPoly\":{\"vertices\":["
                + "{\"x\":" + x + ",\"y\":" + y + "},{\"x\":" + (x + 60) + ",\"y\":" + y + "},{\"x\":" + (x + 60) + ",\"y\":" + (y + 20)
                + "},{\"x\":" + x + ",\"y\":" + (y + 20) + "}]}}";
    }

    private static String wrap(String fieldsJson) {
        return "{\"version\":\"V2\",\"images\":[{\"inferResult\":\"SUCCESS\",\"fields\":[" + fieldsJson + "]}]}";
    }

    @Test
    @DisplayName("네이버 문서의 응답 예시를 읽는다: 글자, 좌표(꼭짓점의 최소·최대), 줄(lineBreak)")
    void parsesDocumentationExample() {
        OcrDocument doc = ClovaOcrResponseParser.parse(DOC_EXAMPLE);
        assertThat(doc.words().size()).isEqualTo(3);
        assertThat(doc.words().get(0).text()).isEqualTo("아름다운");
        assertThat(doc.words().get(0).left() == 2713.7295 && doc.words().get(0).right() == 2841.4343).isTrue();
        assertThat(doc.words().get(0).top() == 977.7408 && doc.words().get(0).bottom() == 1277.0492).isTrue();
        assertThat(doc.words().get(0).confidence() > 0.99).isTrue();
        assertThat(doc.lines().size()).isEqualTo(2);
        assertThat(doc.lines().get(0)).isEqualTo("아름다운");
        assertThat(doc.lines().get(1)).isEqualTo("이 세상");
        assertThat(doc.tables().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("lineBreak가 없으면(V1) 글자 위치로 줄을 묶는다")
    void groupsLinesByGeometryWhenNoLineBreakInfo() {
        OcrDocument doc = ClovaOcrResponseParser.parse(wrap(
                field("타이레놀정", 50, 100, null) + "," + field("1", 400, 102, null) + "," + field("3", 520, 99, null) + ","
                        + field("소화제", 50, 160, null) + "," + field("2", 400, 161, null)));
        assertThat(doc.lines().size()).isEqualTo(2);
        assertThat(doc.lines().get(0)).isEqualTo("타이레놀정 1 3");
        assertThat(doc.lines().get(1)).isEqualTo("소화제 2");
    }

    @Test
    @DisplayName("표(tables)를 행·열 순서의 칸 글자로 바꾼다 (줄·단어는 공백으로 이어 붙임)")
    void parsesTables() {
        String json = "{\"images\":[{\"inferResult\":\"SUCCESS\",\"fields\":[],\"tables\":[{\"cells\":["
                + "{\"rowIndex\":0,\"columnIndex\":0,\"cellTextLines\":[{\"cellWords\":[{\"inferText\":\"처방\"},{\"inferText\":\"명칭\"}]}]},"
                + "{\"rowIndex\":0,\"columnIndex\":1,\"cellTextLines\":[{\"cellWords\":[{\"inferText\":\"1회\"}]},{\"cellWords\":[{\"inferText\":\"투약량\"}]}]},"
                + "{\"rowIndex\":1,\"columnIndex\":0,\"cellTextLines\":[{\"cellWords\":[{\"inferText\":\"타이레놀정\"}]}]},"
                + "{\"rowIndex\":1,\"columnIndex\":1,\"cellTextLines\":[{\"cellWords\":[{\"inferText\":\"1\"}]}]}"
                + "]}]}]}";
        OcrDocument doc = ClovaOcrResponseParser.parse(json);
        assertThat(doc.tables().size()).isEqualTo(1);
        assertThat(doc.tables().get(0).rows().size()).isEqualTo(2);
        assertThat(doc.tables().get(0).rows().get(0).get(0)).isEqualTo("처방 명칭");
        assertThat(doc.tables().get(0).rows().get(0).get(1)).isEqualTo("1회 투약량");
        assertThat(doc.tables().get(0).rows().get(1).get(0)).isEqualTo("타이레놀정");
        assertThat(doc.tables().get(0).rows().get(1).get(1)).isEqualTo("1");
    }

    @Test
    @DisplayName("비어 있는 칸은 빈 문자열이다")
    void missingCellsAreEmpty() {
        String json = "{\"images\":[{\"inferResult\":\"SUCCESS\",\"tables\":[{\"cells\":["
                + "{\"rowIndex\":0,\"columnIndex\":0,\"cellTextLines\":[{\"cellWords\":[{\"inferText\":\"A\"}]}]},"
                + "{\"rowIndex\":1,\"columnIndex\":2,\"cellTextLines\":[{\"cellWords\":[{\"inferText\":\"B\"}]}]}]}]}]}";
        OcrDocument doc = ClovaOcrResponseParser.parse(json);
        assertThat(doc.tables().get(0).rows().get(0).size()).isEqualTo(3);
        assertThat(doc.tables().get(0).rows().get(0).get(1)).isEqualTo("");
        assertThat(doc.tables().get(0).rows().get(1).get(2)).isEqualTo("B");
    }

    @Test
    @DisplayName("FAILURE(글자를 못 읽음)는 오류가 아니라 빈 결과다")
    void failureIsEmptyResult() {
        OcrDocument doc = ClovaOcrResponseParser.parse("{\"images\":[{\"inferResult\":\"FAILURE\",\"message\":\"x\"}]}");
        assertThat(doc.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("SUCCESS인데 글자가 하나도 없어도 빈 결과다")
    void successWithNoFields() {
        assertThat(ClovaOcrResponseParser.parse("{\"images\":[{\"inferResult\":\"SUCCESS\",\"fields\":[]}]}").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("ERROR(엔진 처리 오류)와 알 수 없는 응답은 엔진을 쓸 수 없는 것으로 본다")
    void errorsAreUnavailable() {
        assertThat(reasonOf("{\"images\":[{\"inferResult\":\"ERROR\"}]}")).isEqualTo("infer-error");
        assertThat(reasonOf("not json")).isEqualTo("unexpected-response");
        assertThat(reasonOf("")).isEqualTo("unexpected-response");
        assertThat(reasonOf("[]")).isEqualTo("unexpected-response");
        assertThat(reasonOf("{}")).isEqualTo("unexpected-response");
        assertThat(reasonOf("{\"images\":[]}")).isEqualTo("unexpected-response");
        assertThat(reasonOf("{\"images\":[{\"message\":\"x\"}]}")).isEqualTo("unexpected-response");
        assertThat(reasonOf(null)).isEqualTo("unexpected-response");
    }

    @Test
    @DisplayName("오류에 응답 내용(처방전 글자)이 들어가지 않는다")
    void reasonNeverContainsText() {
        String reason = reasonOf("{\"images\":[{\"inferResult\":\"ERROR\",\"message\":\"홍길동 처방전\"}]}");
        assertThat(reason.contains("홍길동")).isFalse();
    }

    @Test
    @DisplayName("글자가 비어 있거나 신뢰도가 없는 조각도 안전하게 처리한다")
    void toleratesOddFields() {
        OcrDocument doc = ClovaOcrResponseParser.parse(wrap("{\"inferText\":\"  \"},{\"inferText\":\"약\",\"lineBreak\":true},{\"x\":1},\"str\""));
        assertThat(doc.words().size()).isEqualTo(1);
        assertThat(doc.words().get(0).confidence() == 1.0).isTrue();
        assertThat(doc.words().get(0).left() == 0.0).isTrue();
        assertThat(doc.lines().get(0)).isEqualTo("약");
    }

    @Test
    @DisplayName("줄이 lineBreak 없이 끝나도 마지막 줄을 잃지 않는다")
    void flushesLastLine() {
        OcrDocument doc = ClovaOcrResponseParser.parse(wrap(field("가", 10, 10, true) + "," + field("나", 10, 40, false) + "," + field("다", 80, 40, false)));
        assertThat(doc.lines().size()).isEqualTo(2);
        assertThat(doc.lines().get(1)).isEqualTo("나 다");
    }
}