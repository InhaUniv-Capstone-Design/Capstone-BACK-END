package com.gamcho.yakyeon.domain.medication.ocr.clova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 약국 복약안내문·약봉투 양식(복용법이 "1정식 2회 30일분" 같은 문장) 해석 시험.
 *
 * 시험 자료는 모두 네이버 CLOVA 콘솔에 이미지를 올려 받은 실제 응답이다(이름·번호는 모두 가상):
 *  - pillbag_real_a.json: 팀이 쓰는 시험 이미지(조제약 복약안내)
 *  - pillbag_b.json / pillbag_c.json / pillbag_d.json: 샘플 이미지 3장(B 같은 양식 5종, C 다른 약국 양식, D 라벨형)
 */
class PillBagTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static OcrResult parse(String resource) throws IOException {
        try (InputStream in = PillBagTextParserTest.class.getResourceAsStream("/ocr/" + resource)) {
            assertThat(in != null).as(resource + " 시험 자료가 있다").isTrue();
            OcrDocument doc = ClovaOcrResponseParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return PrescriptionTextParser.parse(doc, TODAY);
        }
    }

    private static String line(OcrResult.Item i) {
        return i.drugName() + " | " + i.dosePerIntake() + " | " + i.timesPerDay() + " | " + i.totalDays() + " | " + i.prn() + " | "
                + i.mealRelation() + " | " + i.mealOffsetMin() + " | " + i.timingSlots();
    }

    // ------------------------------------------------------------------ 실제 CLOVA 응답

    @Test
    @DisplayName("실제 CLOVA 응답(팀의 시험 이미지): 약 4개를 이름·1회량·횟수·일수·시간대까지 읽는다")
    void realClovaResponse() throws IOException {
        OcrResult r = parse("pillbag_real_a.json");
        assertThat(r.items().size()).isEqualTo(4);
        assertThat(line(r.items().get(0))).isEqualTo("아모디핀정 5mg | 1.00 | 2 | 30 | false | null | null | [MORNING, DINNER]");
        assertThat(line(r.items().get(1))).isEqualTo("메토프로민정 500mg | 1.00 | 3 | 30 | false | null | null | [MORNING, LUNCH, DINNER]");
        assertThat(line(r.items().get(2))).isEqualTo("로수바스타틴정 10mg | 1.00 | 1 | 30 | false | null | null | [DINNER]");
        assertThat(line(r.items().get(3))).as("'매 식후'는 끼니마다 식후: 하루 3회면 아침·점심·저녁").isEqualTo("새로운 보조약 100mg | 1.00 | 3 | 30 | false | AFTER_MEAL | null | [MORNING, LUNCH, DINNER]");
    }

    @Test
    @DisplayName("실제 응답: 영문 병기, 효능 설명, 약 사진, 주의사항 아이콘 문구, 영수증 잡음이 약 이름이나 값에 섞이지 않는다")
    void realClovaResponseHasNoLeakage() throws IOException {
        OcrResult r = parse("pillbag_real_a.json");
        for (OcrResult.Item i : r.items()) {
            assertThat(i.drugName().contains("(")).as(i.drugName() + " 영문 병기 제거").isFalse();
            assertThat(i.drugName().contains("치료제") || i.drugName().contains("약입니다")).as(i.drugName() + " 효능 설명이 섞이지 않음").isFalse();
        }
    }

    @Test
    @DisplayName("실제 응답: 병원은 '내과의원', 처방일은 교부번호의 날짜(2024-05-20)이고 '다음내방일'은 처방일로 쓰지 않는다")
    void realClovaResponseHospitalAndDate() throws IOException {
        OcrResult r = parse("pillbag_real_a.json");
        assertThat(r.hospitalName()).isEqualTo("내과의원");
        assertThat(String.valueOf(r.prescribedDate())).isEqualTo("2024-05-20");
    }

    // ------------------------------------------------------------------ 샘플 이미지 3장 (실제 CLOVA 응답)

    @Test
    @DisplayName("샘플 B (같은 양식, 5종): 취침, 반 알(½), 식후 30분, 아래 줄이 셋인 시간대를 읽고, 같은 줄 오른쪽의 아이콘 문구('식후 즉시')는 섞이지 않는다")
    void sampleB() throws IOException {
        OcrResult r = parse("pillbag_b.json");
        assertThat(r.items().size()).isEqualTo(5);
        assertThat(line(r.items().get(0))).isEqualTo("메트포르민정 500mg | 1.00 | 2 | 14 | false | null | null | [MORNING, DINNER]");
        assertThat(line(r.items().get(1))).isEqualTo("암로디핀정 5mg | 1.00 | 1 | 14 | false | AFTER_MEAL | 30 | [MORNING]");
        assertThat(line(r.items().get(2))).as("취침").isEqualTo("아토르바스타틴정 10mg | 1.00 | 1 | 14 | false | null | null | [BEDTIME]");
        assertThat(line(r.items().get(3))).isEqualTo("세티리진정 10mg | 1.00 | 1 | 7 | false | AFTER_MEAL | null | [DINNER]");
        assertThat(line(r.items().get(4))).as("½정식, 아침·점심·저녁 세 줄").isEqualTo("아세트아미노펜정 500mg | 0.50 | 3 | 5 | false | null | null | [MORNING, LUNCH, DINNER]");
        assertThat(r.hospitalName()).isEqualTo("행복내과의원");
        assertThat(String.valueOf(r.prescribedDate())).isEqualTo("2026-11-05");
    }

    @Test
    @DisplayName("샘플 C (다른 약국: 번호 칸, '1회 1정, 1일 3회, 7일분'): 번호가 약 이름에 섞이지 않고 시간대가 없으면 빈 목록이다")
    void sampleC() throws IOException {
        OcrResult r = parse("pillbag_c.json");
        assertThat(r.items().size()).isEqualTo(3);
        assertThat(line(r.items().get(0))).isEqualTo("레보세티리진정 5mg | 1.00 | 1 | 7 | false | AFTER_MEAL | null | [DINNER]");
        assertThat(line(r.items().get(1))).as("시간대 글자가 없으면 시간대는 빈 목록 (사용자가 고른다)").isEqualTo("아목시실린클라불란산정 625mg | 1.00 | 3 | 7 | false | AFTER_MEAL | 30 | []");
        assertThat(line(r.items().get(2))).isEqualTo("모사프리드정 5mg | 1.00 | 3 | 7 | false | AFTER_MEAL | 30 | []");
    }

    @Test
    @DisplayName("샘플 D (라벨형, '1일 3회, 1회 1정, 10일분', 가장자리 영수증 잡음): 4개를 읽는다")
    void sampleD() throws IOException {
        OcrResult r = parse("pillbag_d.json");
        assertThat(r.items().size()).isEqualTo(4);
        assertThat(line(r.items().get(0))).isEqualTo("트라조돈정 50mg | 1.00 | 1 | 10 | false | null | null | [BEDTIME]");
        assertThat(line(r.items().get(1))).isEqualTo("에스오메프라졸캡슐 20mg | 1.00 | 1 | 10 | false | BEFORE_MEAL | 30 | [MORNING]");
        assertThat(line(r.items().get(2))).isEqualTo("프로바이오틱스캡슐 | 1.00 | 2 | 10 | false | null | null | [MORNING, DINNER]");
        assertThat(line(r.items().get(3))).isEqualTo("비타민D정 1000IU | 1.00 | 1 | 10 | false | AFTER_MEAL | null | [MORNING]");
    }

    // ------------------------------------------------------------------ 문장 변형과 실패 경우 (작은 표로 직접 만든 글자 위치)

    private static OcrDocument.Word w(String text, double left, double top) {
        return new OcrDocument.Word(text, left, top, left + 12 * text.length(), top + 20, 0.99);
    }

    /** 머리글 한 줄 + 약 한 줄(이름 칸, 복용 칸) 짜리 표. extra는 복용 칸의 아래 줄들 */
    private static OcrDocument oneRow(String name, String sentence, String... extra) {
        List<OcrDocument.Word> words = new ArrayList<>();
        words.add(w("약품명", 200, 100));
        words.add(w("복약안내", 600, 100));
        double x = 200;
        for (String token : name.split(" ")) {
            words.add(w(token, x, 150));
            x += 12 * token.length() + 8;
        }
        addLine(words, sentence, 600, 150);
        for (int i = 0; i < extra.length; i++) {
            addLine(words, extra[i], 600, 180 + i * 30);
        }
        words.add(w("복약", 600, 300));
        words.add(w("지도", 650, 300));
        return new OcrDocument(words, List.of(), List.of());
    }

    private static void addLine(List<OcrDocument.Word> words, String text, double left, double top) {
        double x = left;
        for (String token : text.split(" ")) {
            words.add(w(token, x, top));
            x += 12 * token.length() + 6;
        }
    }

    private static List<OcrResult.Item> items(OcrDocument doc) {
        return PillBagTextParser.itemsFromWords(doc.words());
    }

    @Test
    @DisplayName("문장 순서 세 가지를 읽는다: '1정식 2회 30일분', '1회 2정, 1일 3회, 5일분', '1일 2회, 1회 ½정, 7일분'")
    void sentenceOrders() {
        assertThat(line(items(oneRow("가나정 5mg", "1정식 2회 30일분", "아침 1정, 저녁 1정")).get(0))).isEqualTo("가나정 5mg | 1.00 | 2 | 30 | false | null | null | [MORNING, DINNER]");
        assertThat(line(items(oneRow("다라정", "1회 2정, 1일 3회, 5일분", "식후 30분")).get(0))).isEqualTo("다라정 | 2.00 | 3 | 5 | false | AFTER_MEAL | 30 | []");
        assertThat(line(items(oneRow("마바정", "1일 2회, 1회 ½정, 7일분", "아침, 저녁")).get(0))).isEqualTo("마바정 | 0.50 | 2 | 7 | false | null | null | [MORNING, DINNER]");
    }

    @Test
    @DisplayName("글자 인식이 '정씩'을 '정식'으로 읽거나 '일분' 없이 '일'만 읽어도 값을 읽는다")
    void tolerantSentence() {
        assertThat(line(items(oneRow("사아정", "1정씩 3회 7일", "아침 1정, 점심 1정, 저녁 1정")).get(0))).isEqualTo("사아정 | 1.00 | 3 | 7 | false | null | null | [MORNING, LUNCH, DINNER]");
    }

    @Test
    @DisplayName("시간대 글자의 개수가 하루 횟수와 어긋나면 시간대는 비운다 (틀린 시간대를 알려 주느니 사용자가 고르게)")
    void slotCountMismatchDropsSlots() {
        OcrResult.Item item = items(oneRow("자차정", "1정식 3회 7일분", "아침 1정, 저녁 1정")).get(0);
        assertThat(item.timesPerDay().toString()).isEqualTo("3");
        assertThat(item.timingSlots().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("'매 식후'는 하루 횟수만큼 아침·점심·저녁(·취침)으로 풀고, 횟수가 1이면 시간대는 비운다")
    void everyMeal() {
        assertThat(items(oneRow("카타정", "1정식 3회 7일분", "매 식후")).get(0).timingSlots().toString()).isEqualTo("[MORNING, LUNCH, DINNER]");
        assertThat(items(oneRow("파하정", "1정식 2회 7일분", "매 식후")).get(0).timingSlots().toString()).isEqualTo("[MORNING, DINNER]");
        assertThat(items(oneRow("가가정", "1정식 1회 7일분", "매 식후")).get(0).timingSlots().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("'필요시'가 적힌 약은 시간대와 하루 횟수를 갖지 않는다 (DB 규칙)")
    void prnHasNoSlotsOrTimes() {
        OcrResult.Item item = items(oneRow("나나정", "1정식 2회 5일분", "아침 1정, 저녁 1정 필요시")).get(0);
        assertThat(item.prn()).isTrue();
        assertThat(item.timesPerDay() == null).isTrue();
        assertThat(item.timingSlots().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("복용 문장이 하나도 없는 문서(처방전 양식 등)는 빈 목록이라 기존 해석으로 넘어간다")
    void noSentenceFallsThrough() {
        List<OcrDocument.Word> words = new ArrayList<>();
        words.add(w("처방", 100, 100));
        words.add(w("의약품의", 200, 100));
        words.add(w("명칭", 300, 100));
        words.add(w("1회", 400, 100));
        words.add(w("투약량", 460, 100));
        words.add(w("아모디핀정", 100, 150));
        words.add(w("5mg", 220, 150));
        words.add(w("1", 400, 150));
        words.add(w("2", 520, 150));
        words.add(w("30", 600, 150));
        assertThat(PillBagTextParser.itemsFromWords(words).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("값이 범위를 벗어난 문장(하루 9회, 일수 0)은 약으로 읽지 않는다")
    void outOfRangeSentenceIsIgnored() {
        assertThat(items(oneRow("다다정", "1정식 9회 30일분")).isEmpty()).isTrue();
        assertThat(items(oneRow("라라정", "1정식 2회 0일분")).isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------ 날짜

    @Test
    @DisplayName("교부번호 안의 날짜('20240520-001')를 처방일로 읽고, 형식이 다른 숫자는 날짜로 보지 않는다")
    void issueNumberDate() {
        assertThat(String.valueOf(PrescriptionTextParser.findDate(List.of("교부번호 : 20240520-001"), TODAY))).isEqualTo("2024-05-20");
        assertThat(PrescriptionTextParser.findDate(List.of("교부번호 : 20241320-001"), TODAY) == null).as("13월은 날짜가 아니다").isTrue();
        assertThat(PrescriptionTextParser.findDate(List.of("전화 20240520-001"), TODAY) == null).as("교부번호 이름표가 없으면 쓰지 않는다").isTrue();
    }

    @Test
    @DisplayName("'다음내방일'처럼 앞으로 올 날짜는 처방일이 아니다")
    void nextVisitDateIsNotThePrescriptionDate() {
        assertThat(PrescriptionTextParser.findDate(List.of("(다음내방일 : 2026-11-19 )"), TODAY) == null).isTrue();
    }
}