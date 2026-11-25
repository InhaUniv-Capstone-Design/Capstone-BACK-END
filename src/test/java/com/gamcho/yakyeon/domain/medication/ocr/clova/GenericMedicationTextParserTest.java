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
 * 양식을 가정하지 않는 범용 해석(마지막 방법) 시험.
 * generic_e/f/g/h.json은 약국마다 다를 만한 약봉투 양식 4가지를 그린 글자 위치로 만든 CLOVA 응답 모양의 가상 데이터이다(실제 응답이 아니다).
 * 실제 CLOVA 응답이 생기면 이 파일들을 실제 응답으로 바꾼다.
 */
class GenericMedicationTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static OcrResult parse(String resource) throws IOException {
        try (InputStream in = GenericMedicationTextParserTest.class.getResourceAsStream("/ocr/" + resource)) {
            assertThat(in != null).as(resource + " 시험 자료가 있다").isTrue();
            return PrescriptionTextParser.parse(ClovaOcrResponseParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)), TODAY);
        }
    }

    private static String line(OcrResult.Item i) {
        return i.drugName() + " | " + i.dosePerIntake() + " | " + i.timesPerDay() + " | " + i.totalDays() + " | " + i.prn() + " | "
                + i.mealRelation() + " | " + i.mealOffsetMin() + " | " + i.timingSlots();
    }

    private static List<OcrResult.Item> items(String... rows) {
        return GenericMedicationTextParser.items(List.of(rows));
    }

    // ------------------------------------------------------------------ 처음 보는 양식 4가지

    @Test
    @DisplayName("양식 E (조제 라벨): 복용법이 봉투 전체에 한 번만 적혀 있으면 모든 약의 기본값으로 쓴다")
    void labelWithOneInstructionForAllDrugs() throws IOException {
        OcrResult r = parse("generic_e.json");
        assertThat(r.items().size()).isEqualTo(3);
        assertThat(line(r.items().get(0))).as("1회량은 적혀 있지 않으니 비어 있다").isEqualTo("아모디핀정 5mg | null | 3 | 3 | false | AFTER_MEAL | 30 | []");
        assertThat(line(r.items().get(1))).isEqualTo("메토프로민정 500mg | null | 3 | 3 | false | AFTER_MEAL | 30 | []");
        assertThat(line(r.items().get(2))).isEqualTo("로수바스타틴정 10mg | null | 3 | 3 | false | AFTER_MEAL | 30 | []");
        assertThat(r.pharmacyName()).isEqualTo("온누리약국");
        assertThat(String.valueOf(r.prescribedDate())).as("조제일").isEqualTo("2026-11-05");
    }

    @Test
    @DisplayName("양식 F (분포 약봉투): 약 목록 위의 큰 제목('아침 식후 30분')을 모든 약의 시간대와 식사 관계로 쓴다")
    void pouchWithTitleAboveTheList() throws IOException {
        OcrResult r = parse("generic_f.json");
        assertThat(r.items().size()).isEqualTo(3);
        assertThat(line(r.items().get(0))).isEqualTo("아모디핀정 5mg | 1.00 | 1 | null | false | AFTER_MEAL | 30 | [MORNING]");
        assertThat(line(r.items().get(2))).isEqualTo("로수바스타틴정 10mg | 1.00 | 1 | null | false | AFTER_MEAL | 30 | [MORNING]");
    }

    @Test
    @DisplayName("양식 G (줄글 복약지도): 약마다 아래 줄의 문장에서 횟수·일수·시간대·식사 관계를 읽는다 ('하루 2번', '자기 전')")
    void proseInstructionsPerDrug() throws IOException {
        OcrResult r = parse("generic_g.json");
        assertThat(r.items().size()).isEqualTo(3);
        assertThat(line(r.items().get(0))).isEqualTo("아모디핀정 5mg | 1.00 | 2 | 7 | false | AFTER_MEAL | 30 | [MORNING, DINNER]");
        assertThat(line(r.items().get(1))).isEqualTo("메토프로민정 500mg | 1.00 | 3 | 5 | false | AFTER_MEAL | null | []");
        assertThat(line(r.items().get(2))).as("자기 전은 취침").isEqualTo("세티리진정 10mg | 1.00 | 1 | 7 | false | null | null | [BEDTIME]");
    }

    @Test
    @DisplayName("양식 H (한 줄 압축형): 띄어쓰기 없는 '1일2회', 약어 '1T', 일 수만 적힌 '7일', '저녁식후'를 읽는다")
    void compactSingleLines() throws IOException {
        OcrResult r = parse("generic_h.json");
        assertThat(r.items().size()).isEqualTo(3);
        assertThat(line(r.items().get(0))).isEqualTo("아모디핀정 5mg | 1.00 | 2 | 7 | false | AFTER_MEAL | 30 | []");
        assertThat(line(r.items().get(1))).as("1T = 1정").isEqualTo("메토프로민정 500mg | 1.00 | 3 | 5 | false | null | null | []");
        assertThat(line(r.items().get(2))).isEqualTo("로수바스타틴정 10mg | 1.00 | 1 | 7 | false | AFTER_MEAL | null | [DINNER]");
    }

    // ------------------------------------------------------------------ 값 하나하나

    @Test
    @DisplayName("이름만 있고 복용 정보가 없는 봉투도 이름은 돌려준다 (값은 비어 있다)")
    void namesOnlyWhenNothingElseIsReadable() {
        List<OcrResult.Item> r = items("조제 약품 목록", "아모디핀정 5mg", "메토프로민정 500mg");
        assertThat(r.size()).isEqualTo(2);
        assertThat(line(r.get(0))).isEqualTo("아모디핀정 5mg | null | null | null | false | null | null | []");
    }

    @Test
    @DisplayName("함량만 붙은 이름('새로운 보조약 100mg')도 읽는다")
    void nameWithStrengthButNoDosageForm() {
        assertThat(line(items("새로운 보조약 100mg 1정 하루 3번 5일분").get(0))).isEqualTo("새로운 보조약 100mg | 1.00 | 3 | 5 | false | null | null | []");
    }

    @Test
    @DisplayName("'1회 1정'의 1회를 하루 횟수로 읽지 않는다")
    void oncePerDoseIsNotTimesPerDay() {
        assertThat(line(items("아모디핀정 5mg 1회 1정 7일분").get(0))).isEqualTo("아모디핀정 5mg | 1.00 | null | 7 | false | null | null | []");
    }

    @Test
    @DisplayName("총 개수('30정')를 1회 투약량으로 읽지 않는다")
    void totalCountIsNotTheDose() {
        assertThat(line(items("아모디핀정 5mg 총 30정").get(0))).isEqualTo("아모디핀정 5mg | null | null | null | false | null | null | []");
    }

    @Test
    @DisplayName("한글 숫자('하루 세 번')와 '1일 3회'를 모두 읽는다")
    void koreanNumerals() {
        assertThat(line(items("가나정 5mg 하루 세 번 1정씩 3일분").get(0))).isEqualTo("가나정 5mg | 1.00 | 3 | 3 | false | null | null | []");
        assertThat(line(items("다라정 5mg 1일 4회 1정 2일분").get(0))).isEqualTo("다라정 5mg | 1.00 | 4 | 2 | false | null | null | []");
    }

    @Test
    @DisplayName("'매 식후'는 하루 횟수만큼 시간대로 풀고, '필요시'는 시간대와 횟수를 비운다")
    void everyMealAndPrn() {
        assertThat(items("마바정 5mg 1정씩 1일 3회 5일분 매 식후").get(0).timingSlots().toString()).isEqualTo("[MORNING, LUNCH, DINNER]");
        OcrResult.Item prn = items("사아정 5mg 1정씩 통증시 복용 5일분").get(0);
        assertThat(prn.prn()).isTrue();
        assertThat(prn.timesPerDay() == null).isTrue();
        assertThat(prn.timingSlots().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("시간대 글자 개수가 하루 횟수와 어긋나면 시간대는 비운다")
    void slotCountMismatch() {
        OcrResult.Item item = items("자차정 5mg 1정씩 1일 3회 아침 저녁 5일분").get(0);
        assertThat(item.timesPerDay().toString()).isEqualTo("3");
        assertThat(item.timingSlots().isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------ 오탐 (약이 아닌 글을 약으로 읽지 않는다)

    @Test
    @DisplayName("행정 문구와 영수증 글(일정, 예정, 확정, 금액, 총액, 약국, 보험)은 약으로 읽지 않는다")
    void administrativeTextIsNotADrug() {
        assertThat(items("환자명 홍길동 2026-11-05 조제", "진료 일정 확정 예정 안정 결정", "약국 금액 총액 5,000원 보험 공단 부담금", "처방전 교부번호 20261105-001 용법 용량 효능 주의").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("함량이 붙어도 용법·성명 같은 머리글이나 안내 문구는 약 이름이 아니다")
    void labelsWithStrengthAreNotDrugs() {
        assertThat(items("약품명 용량 5mg", "환자 성명 홍길동 복용량 2정").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("글이 비어 있으면 빈 목록이다")
    void emptyInput() {
        assertThat(GenericMedicationTextParser.items(List.of()).isEmpty()).isTrue();
        assertThat(items("", "  ").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("같은 약이 두 번 나와도 한 번만 돌려준다")
    void sameDrugIsReturnedOnce() {
        assertThat(items("아모디핀정 5mg 1정씩 1일 2회 7일분", "아모디핀정 5mg").size()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 작은 글자 (주의사항 아이콘 밑 설명)

    private static OcrDocument.Word w(String text, double left, double top, double height) {
        return new OcrDocument.Word(text, left, top, left + 12 * text.length(), top + height, 0.99);
    }

    @Test
    @DisplayName("같은 줄에 있는 작은 글자('식후 즉시' 같은 아이콘 설명)는 약의 복용 정보로 읽지 않는다")
    void smallPrintOnTheSameLineIsIgnored() {
        List<OcrDocument.Word> words = new ArrayList<>();
        words.add(w("메트포르민정", 100, 100, 24));
        words.add(w("500mg", 230, 100, 24));
        words.add(w("1정씩", 320, 100, 24));
        words.add(w("1일", 400, 100, 24));
        words.add(w("2회", 440, 100, 24));
        words.add(w("14일분", 480, 100, 24));
        words.add(w("식후", 600, 104, 16));   // 글자가 작다: 주의사항 아이콘 설명
        words.add(w("즉시", 640, 104, 16));
        List<OcrResult.Item> result = GenericMedicationTextParser.items(GenericMedicationTextParser.rowTexts(WordRows.group(words)));
        assertThat(line(result.get(0))).isEqualTo("메트포르민정 500mg | 1.00 | 2 | 14 | false | null | null | []");
    }
}