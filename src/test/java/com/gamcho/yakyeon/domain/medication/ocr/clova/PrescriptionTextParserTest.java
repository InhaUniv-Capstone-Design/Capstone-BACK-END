package com.gamcho.yakyeon.domain.medication.ocr.clova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrescriptionTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static OcrDocument.Word w(String text, double x, double y) {
        return new OcrDocument.Word(text, x, y, x + 12.0 * text.length(), y + 20, 0.99);
    }

    private static OcrDocument ofWords(OcrDocument.Word... words) {
        return new OcrDocument(List.of(words), List.of(), List.of());
    }

    private static OcrDocument ofLines(String... lines) {
        return new OcrDocument(List.of(), List.of(lines), List.of());
    }

    private static String dose(OcrResult.Item item) {
        return item.dosePerIntake() == null ? null : item.dosePerIntake().toPlainString();
    }

    private static String meal(OcrResult.Item item) {
        return item.mealRelation() == null ? null : item.mealRelation().name();
    }

    /** 일반적인 처방전의 모양: 제목, 의료기관, 교부일, 표(머리글 + 약 4줄), 안내, 조제약국 */
    private static OcrDocument typicalPrescription() {
        List<OcrDocument.Word> list = new ArrayList<>(List.of(
                w("처방전", 400, 40),
                w("의료기관명칭", 50, 100), w("서울튼튼병원", 250, 100),
                w("교부연월일", 50, 140), w("2026", 250, 140), w("년", 300, 140), w("11", 330, 140), w("월", 360, 140), w("05", 390, 140), w("일", 420, 140),
                // 표 머리글
                w("처방", 50, 200), w("의약품의", 110, 200), w("명칭", 230, 200), w("1회", 420, 200), w("투약량", 470, 200),
                w("1일", 580, 200), w("투여횟수", 620, 200), w("총", 740, 200), w("투약일수", 770, 200), w("용법", 900, 200),
                // 약 1: 이름 + 식후 30분
                w("타이레놀정500mg", 50, 240), w("1", 430, 240), w("3", 600, 240), w("3", 760, 240), w("식후", 900, 240), w("30분", 960, 240),
                // 약 2: 보험코드가 앞에 붙은 이름
                w("[644900010]", 50, 280), w("메트포르민정", 150, 280), w("500mg", 270, 280), w("1", 430, 281), w("2", 600, 279), w("30", 760, 280),
                w("식후", 900, 280), w("30분", 960, 280),
                // 약 3: 필요 시 복용
                w("겔포스엠현탁액", 50, 320), w("1", 430, 320), w("3", 600, 320), w("5", 760, 320), w("필요시", 900, 320),
                // 약 4: 반 알, 식후만
                w("아스피린프로텍트정100mg", 50, 360), w("1/2", 430, 360), w("1", 600, 360), w("30", 760, 360), w("식후", 900, 360),
                // 안내와 조제약국
                w("조제시", 50, 420), w("참고사항", 130, 420),
                w("아세트아미노펜정", 50, 460), w("1", 430, 460), w("3", 600, 460), w("3", 760, 460),
                w("조제약국", 50, 520), w("행복약국", 160, 520)));
        return new OcrDocument(list, List.of(), List.of());
    }

    // ---------------------------------------------------------------- 위치로 묶은 행

    @Test
    @DisplayName("일반적인 처방전: 병원, 약국, 교부일, 약 4개를 읽는다")
    void readsTypicalPrescription() {
        OcrResult r = PrescriptionTextParser.parse(typicalPrescription(), TODAY);
        assertThat(r.hospitalName()).isEqualTo("서울튼튼병원");
        assertThat(r.pharmacyName()).isEqualTo("행복약국");
        assertThat(r.prescribedDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(r.items().size()).as("안내 문구 아래의 줄은 약이 아니다").isEqualTo(4);

        OcrResult.Item a = r.items().get(0);
        assertThat(a.drugName()).isEqualTo("타이레놀정500mg");
        assertThat(dose(a)).isEqualTo("1.00");
        assertThat(a.timesPerDay()).isEqualTo((short) 3);
        assertThat(a.totalDays()).isEqualTo(3);
        assertThat(a.prn()).isFalse();
        assertThat(meal(a)).isEqualTo("AFTER_MEAL");
        assertThat(a.mealOffsetMin()).isEqualTo((short) 30);

        OcrResult.Item b = r.items().get(1);
        assertThat(b.drugName()).as("보험코드는 이름이 아니다").isEqualTo("메트포르민정 500mg");
        assertThat(b.timesPerDay()).isEqualTo((short) 2);
        assertThat(b.totalDays()).isEqualTo(30);

        OcrResult.Item c = r.items().get(2);
        assertThat(c.drugName()).isEqualTo("겔포스엠현탁액");
        assertThat(c.prn()).isTrue();
        assertThat(c.timesPerDay() == null).as("필요 시 복용은 하루 횟수가 없다").isTrue();
        assertThat(c.totalDays()).isEqualTo(5);

        OcrResult.Item d = r.items().get(3);
        assertThat(d.drugName()).isEqualTo("아스피린프로텍트정100mg");
        assertThat(dose(d)).isEqualTo("0.50");
        assertThat(meal(d)).isEqualTo("AFTER_MEAL");
        assertThat(d.mealOffsetMin() == null).as("분이 적혀 있지 않으면 비워 둔다").isTrue();
    }

    @Test
    @DisplayName("머리글이 두 줄로 나뉘어 있어도 약을 읽는다")
    void readsSplitHeader() {
        OcrDocument doc = ofWords(
                w("처방", 50, 200), w("의약품의", 110, 200), w("1회", 420, 200), w("1일", 580, 200), w("총", 740, 200), w("용법", 900, 200),
                w("명칭", 230, 228), w("투약량", 470, 228), w("투여횟수", 620, 228), w("투약일수", 770, 228),
                w("타이레놀정500mg", 50, 270), w("1", 430, 270), w("3", 600, 270), w("3", 760, 270), w("식전", 900, 270), w("30분", 960, 270));
        OcrResult r = PrescriptionTextParser.parse(doc, TODAY);
        assertThat(r.items().size()).isEqualTo(1);
        assertThat(r.items().get(0).drugName()).isEqualTo("타이레놀정500mg");
        assertThat(meal(r.items().get(0))).isEqualTo("BEFORE_MEAL");
        assertThat(r.items().get(0).mealOffsetMin()).isEqualTo((short) 30);
    }

    @Test
    @DisplayName("용법이 다음 행으로 내려가면 앞 약의 용법으로 합친다")
    void mergesUsageOnNextRow() {
        OcrDocument doc = ofLines(
                "처방 의약품의 명칭 1회 투약량 1일 투여횟수 총 투약일수 용법",
                "타이레놀정500mg 1 3 3",
                "식후 30분",
                "조제시 참고사항");
        OcrResult.Item item = PrescriptionTextParser.parse(doc, TODAY).items().get(0);
        assertThat(meal(item)).isEqualTo("AFTER_MEAL");
        assertThat(item.mealOffsetMin()).isEqualTo((short) 30);
    }

    @Test
    @DisplayName("이름 안의 함량 숫자(5/50mg, 1000)가 투약량 칸으로 오인되지 않는다")
    void strengthInNameIsNotDose() {
        OcrResult r = PrescriptionTextParser.parse(ofLines(
                "처방 의약품의 명칭 1회 투약량 1일 투여횟수 총 투약일수 용법",
                "아모잘탄정 5/50mg 1 1 30 식후",
                "비타민씨 1000 1 3 7",
                "조제시 참고사항"), TODAY);
        assertThat(r.items().size()).isEqualTo(2);
        assertThat(r.items().get(0).drugName()).isEqualTo("아모잘탄정 5/50mg");
        assertThat(dose(r.items().get(0))).isEqualTo("1.00");
        assertThat(r.items().get(0).timesPerDay()).isEqualTo((short) 1);
        assertThat(r.items().get(0).totalDays()).isEqualTo(30);
        assertThat(r.items().get(1).drugName()).isEqualTo("비타민씨 1000");
        assertThat(r.items().get(1).totalDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("범위를 벗어난 숫자는 비워 둔다: 하루 5회 이상, 366일 이상")
    void outOfRangeNumbersAreBlank() {
        OcrResult r = PrescriptionTextParser.parse(ofLines(
                "처방 의약품의 명칭 1회 투약량 1일 투여횟수 총 투약일수 용법",
                "알약정 1 6 3",
                "시럽액 1 3 400"), TODAY);
        assertThat(r.items().size()).isEqualTo(2);
        assertThat(r.items().get(0).timesPerDay() == null).as("DB는 하루 1~4회만 받는다").isTrue();
        assertThat(r.items().get(0).totalDays()).isEqualTo(3);
        assertThat(r.items().get(1).totalDays() == null).isTrue();
    }

    // ---------------------------------------------------------------- 엔진이 인식한 표

    @Test
    @DisplayName("표가 있으면 머리글 칸 이름으로 열을 찾아 읽는다 (빈 줄과 안내 아래는 건너뜀)")
    void readsRecognizedTable() {
        OcrDocument.Table table = new OcrDocument.Table(List.of(
                List.of("처방 의약품의 명칭", "1회 투약량", "1일 투여횟수", "총 투약일수", "용법"),
                List.of("타이레놀정500mg", "1", "3", "3", "식후 30분"),
                List.of("", "", "", "", ""),
                List.of("소화제", "1", "1", "7", "필요시"),
                List.of("조제시 참고사항", "", "", "", ""),
                List.of("무시되는정", "1", "1", "1", "")));
        OcrResult r = PrescriptionTextParser.parse(new OcrDocument(List.of(), List.of("의료기관명칭 서울튼튼병원"), List.of(table)), TODAY);
        assertThat(r.hospitalName()).isEqualTo("서울튼튼병원");
        assertThat(r.items().size()).isEqualTo(2);
        assertThat(r.items().get(0).drugName()).isEqualTo("타이레놀정500mg");
        assertThat(r.items().get(0).timesPerDay()).isEqualTo((short) 3);
        assertThat(meal(r.items().get(0))).isEqualTo("AFTER_MEAL");
        assertThat(r.items().get(1).drugName()).isEqualTo("소화제");
        assertThat(r.items().get(1).prn()).isTrue();
        assertThat(r.items().get(1).timesPerDay() == null).isTrue();
    }

    @Test
    @DisplayName("열 순서가 달라도 머리글 이름으로 찾는다 (1회 투약량이 횟수 열로 오인되지 않음)")
    void tableColumnOrderFollowsHeader() {
        OcrDocument.Table table = new OcrDocument.Table(List.of(
                List.of("약품명", "총 투약일수", "1일 투여횟수", "1회 투약량"),
                List.of("타이레놀정", "5", "2", "1")));
        OcrResult.Item item = PrescriptionTextParser.parse(new OcrDocument(List.of(), List.of(), List.of(table)), TODAY).items().get(0);
        assertThat(item.totalDays()).isEqualTo(5);
        assertThat(item.timesPerDay()).isEqualTo((short) 2);
        assertThat(dose(item)).isEqualTo("1.00");
    }

    @Test
    @DisplayName("표에서 약이 나오면 글자 행에서 다시 읽지 않는다 (같은 약이 두 번 나오지 않음)")
    void tableTakesPrecedence() {
        OcrDocument base = typicalPrescription();
        OcrDocument.Table table = new OcrDocument.Table(List.of(
                List.of("처방 의약품의 명칭", "1회 투약량", "1일 투여횟수", "총 투약일수", "용법"),
                List.of("타이레놀정500mg", "1", "3", "3", "식후 30분")));
        OcrResult r = PrescriptionTextParser.parse(new OcrDocument(base.words(), List.of(), List.of(table)), TODAY);
        assertThat(r.items().size()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 줄만 있을 때 / 읽을 수 없을 때

    @Test
    @DisplayName("글자 위치 없이 줄만 있어도 읽는다")
    void readsFromLinesOnly() {
        OcrResult r = PrescriptionTextParser.parse(ofLines("처방전", "의료기관명칭 서울튼튼병원", "교부연월일 2026.11.05",
                "처방 의약품의 명칭 1회 투약량 1일 투여횟수 총 투약일수 용법",
                "타이레놀정500mg 1 3 3 식후 30분", "겔포스엠현탁액 1 3 5 필요시", "조제시 참고사항", "조제약국 행복약국"), TODAY);
        assertThat(r.hospitalName()).isEqualTo("서울튼튼병원");
        assertThat(r.pharmacyName()).isEqualTo("행복약국");
        assertThat(r.prescribedDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(r.items().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("처방전이 아닌 글자에서는 아무것도 만들어 내지 않는다")
    void junkProducesNothing() {
        OcrResult r = PrescriptionTextParser.parse(ofLines("안녕하세요", "010-1234-5678", "서울시 강남구 12 3 4", "오늘의 날씨 맑음"), TODAY);
        assertThat(r.items().isEmpty()).isTrue();
        assertThat(r.hospitalName() == null && r.pharmacyName() == null && r.prescribedDate() == null).isTrue();
        assertThat(PrescriptionTextParser.parse(OcrDocument.empty(), TODAY).items().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("머리글을 못 찾으면 약 이름처럼 생긴 줄만 인정한다 (서울 12 3 4 같은 줄은 약이 아님)")
    void withoutHeaderOnlyDrugLikeRows() {
        OcrResult r = PrescriptionTextParser.parse(ofLines("아세트아미노펜정 1 3 3", "서울 12 3 4", "010 1234 5 6"), TODAY);
        assertThat(r.items().size()).isEqualTo(1);
        assertThat(r.items().get(0).drugName()).isEqualTo("아세트아미노펜정");
    }

    @Test
    @DisplayName("같은 약이 두 번 읽히면 한 번만 담는다")
    void dedupesSameItem() {
        OcrResult r = PrescriptionTextParser.parse(ofLines("처방 의약품의 명칭 1회 투약량 1일 투여횟수 총 투약일수 용법",
                "타이레놀정500mg 1 3 3", "타이레놀정500mg 1 3 3"), TODAY);
        assertThat(r.items().size()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 날짜

    @Test
    @DisplayName("생년월일 같은 다른 날짜는 걸러내고 교부일을 고른다")
    void picksPrescriptionDate() {
        assertThat(PrescriptionTextParser.findDate(List.of("생년월일 2012-05-05", "교부연월일 2026.11.05"), TODAY)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(PrescriptionTextParser.findDate(List.of("2026.10.30 조제"), TODAY)).as("이름표가 없어도 범위 안이면 사용").isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(PrescriptionTextParser.findDate(List.of("교부일 2027.01.01"), TODAY) == null).as("미래 날짜").isTrue();
        assertThat(PrescriptionTextParser.findDate(List.of("조제 2025-01-01"), TODAY) == null).as("이름표 없는 날짜는 1년보다 오래되면 거절").isTrue();
        assertThat(PrescriptionTextParser.findDate(List.of("처방일 2025-01-01"), TODAY)).as("이름표가 있으면 5년까지 허용").isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(PrescriptionTextParser.findDate(List.of("2026.13.45"), TODAY) == null).as("없는 날짜").isTrue();
        assertThat(PrescriptionTextParser.findDate(List.of("교부 2026/11/05", "2026.10.01"), TODAY)).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    // ---------------------------------------------------------------- 용법, 투약량, 이름

    @Test
    @DisplayName("용법: 식전·식후·식사와 함께, 분, 필요 시 복용")
    void parsesUsage() {
        assertThat(meal(item("식후 30분"))).isEqualTo("AFTER_MEAL");
        assertThat(item("식후 30분").mealOffsetMin()).isEqualTo((short) 30);
        assertThat(item("식후30분").mealOffsetMin()).isEqualTo((short) 30);
        assertThat(meal(item("식전 30분"))).isEqualTo("BEFORE_MEAL");
        assertThat(meal(item("식사와 함께"))).isEqualTo("WITH_MEAL");
        assertThat(item("식사와 함께").mealOffsetMin() == null).isTrue();
        assertThat(meal(item("1일 3회 식후 즉시"))).isEqualTo("AFTER_MEAL");
        assertThat(item("1일 3회 식후 즉시").mealOffsetMin() == null).isTrue();
        assertThat(item("식후 600분").mealOffsetMin() == null).as("240분을 넘으면 읽지 않음").isTrue();
        assertThat(meal(item("식후 식전"))).as("먼저 나온 것").isEqualTo("AFTER_MEAL");
        assertThat(item("필요시").prn()).isTrue();
        assertThat(item("통증 시 복용").prn()).isTrue();
        assertThat(item("PRN").prn()).isTrue();
        assertThat(item("").prn()).isFalse();
        assertThat(meal(item("")) == null).isTrue();
    }

    private static OcrResult.Item item(String usage) {
        PrescriptionTextParser.Usage u = PrescriptionTextParser.parseUsage(usage);
        return new OcrResult.Item("x정", null, null, null, u.prn(), u.meal(), u.offset());
    }

    @Test
    @DisplayName("1회 투약량: 정수, 소수, 분수, 반 기호, 단위 붙음. 0과 20 초과는 읽지 않음")
    void parsesDose() {
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("1"))).isEqualTo("1.00");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("0.5"))).isEqualTo("0.50");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("1/2"))).isEqualTo("0.50");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("½"))).isEqualTo("0.50");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("1정"))).isEqualTo("1.00");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("2캡슐"))).isEqualTo("2.00");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("1/3"))).isEqualTo("0.33");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("0"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("25"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("1/0"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose(""))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose(null))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.parseDose("x"))).isEqualTo("null");
    }

    @Test
    @DisplayName("약 이름 다듬기: 보험코드, 표 선, 단위만 있는 글자, 머리글 글자")
    void cleansNames() {
        assertThat(PrescriptionTextParser.cleanName("[644900010] 타이레놀정500mg")).isEqualTo("타이레놀정500mg");
        assertThat(PrescriptionTextParser.cleanName("(643502530)아모잘탄정 5/50mg")).isEqualTo("아모잘탄정 5/50mg");
        assertThat(PrescriptionTextParser.cleanName("|타이레놀|")).isEqualTo("타이레놀");
        assertThat(PrescriptionTextParser.cleanName("-타이레놀정")).isEqualTo("타이레놀정");
        assertThat(String.valueOf(PrescriptionTextParser.cleanName("500 mg"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.cleanName("1/2"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.cleanName("정"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.cleanName("의약품의 명칭"))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.cleanName(""))).isEqualTo("null");
        assertThat(String.valueOf(PrescriptionTextParser.cleanName(null))).isEqualTo("null");
        assertThat(PrescriptionTextParser.cleanName("가".repeat(100)).length()).as("너무 긴 이름은 자름").isEqualTo(60);
    }

    @Test
    @DisplayName("약국 이름: 양식 글자(조제약국, 약국 제출용)는 건너뛰고 마지막 것을 쓴다")
    void pharmacyNames() {
        assertThat(PrescriptionTextParser.findPharmacy(List.of("(약국 제출용)", "조제약국 행복약국"))).isEqualTo("행복약국");
        assertThat(PrescriptionTextParser.findPharmacy(List.of("약국명칭 건강약국", "조제약국 행복약국"))).isEqualTo("행복약국");
        assertThat(String.valueOf(PrescriptionTextParser.findPharmacy(List.of("(약국 제출용)")))).isEqualTo("null");
    }

    @Test
    @DisplayName("병원 이름: 이름표를 떼고, 치과의원이 치과로 잘리지 않는다")
    void hospitalNames() {
        assertThat(PrescriptionTextParser.findHospital(List.of("의료기관명칭 서울튼튼병원"))).isEqualTo("서울튼튼병원");
        assertThat(PrescriptionTextParser.findHospital(List.of("의료기관명칭서울튼튼병원"))).isEqualTo("서울튼튼병원");
        assertThat(PrescriptionTextParser.findHospital(List.of("행복치과의원"))).isEqualTo("행복치과의원");
        assertThat(PrescriptionTextParser.findHospital(List.of("행복한의원"))).isEqualTo("행복한의원");
        assertThat(String.valueOf(PrescriptionTextParser.findHospital(List.of("의료기관", "병원")))).isEqualTo("null");
    }

    // ---------------------------------------------------------------- 시간대 칸 양식 (약품명 | 용량 | 투약일수 | 아침 | 점심 | 저녁)

    /** 가운데 좌표(cx, cy) 기준으로 글자 조각을 만든다. 폭은 글자 수에 비례 */
    private static OcrDocument.Word c(String text, double cx, double cy) {
        double halfWidth = 6.0 * text.length();
        return new OcrDocument.Word(text, cx - halfWidth, cy - 10, cx + halfWidth, cy + 10, 0.99);
    }

    /** 실제 시험용 처방전 사진(1086px 폭)의 글자 위치를 그대로 옮긴 것: 환자 정보, 처방일자, 표(약 3개, 영문 병기 두 줄), 복약 지도, 의료기관 */
    private static OcrDocument sampleSlotGridPrescription() {
        List<OcrDocument.Word> list = new ArrayList<>(List.of(
                c("처방전", 535, 75),
                c("환자", 90, 153), c("성명", 150, 153), c(":", 190, 153), c("홍길동", 250, 153),
                c("처방일자", 650, 155), c(":", 718, 155), c("2024-05-20", 820, 155),
                c("생년월일", 120, 211), c(":", 190, 211), c("1990-01-01", 290, 211),
                c("처방번호", 650, 211), c(":", 718, 211), c("20240520-001", 840, 211),
                c("진료과", 640, 272), c("내과", 765, 272),
                c("의료기관", 650, 331), c(":", 718, 331), c("○○○내과의원", 830, 331),
                c("의사명", 640, 389), c("김의사", 770, 389), c("(면허번호", 855, 389), c(":", 905, 389), c("123456)", 960, 389),
                c("Rp.", 60, 487),
                // 표 머리글
                c("No.", 68, 586), c("약품명", 250, 586), c("용량/規格", 484, 586), c("투약일수", 640, 586),
                c("복용법", 840, 557), c("(1일", 890, 557), c("3회)", 935, 557),
                c("아침", 760, 617), c("점심", 875, 617), c("저녁", 991, 617),
                // 약 1: 아침, 저녁
                c("1", 68, 712), c("아모디핀정", 215, 693), c("5mg", 320, 693), c("(Amlodipine)", 252, 732),
                c("1정", 480, 712), c("30일", 639, 712), c("1정", 760, 712), c("-", 875, 712), c("1정", 991, 712),
                // 약 2: 아침, 점심, 저녁
                c("2", 68, 843), c("메토프로민정", 190, 825), c("500mg", 340, 825), c("(Metformin)", 252, 863),
                c("1정", 480, 843), c("30일", 639, 843), c("1정", 760, 843), c("1정", 875, 843), c("1정", 991, 843),
                // 약 3: 저녁만
                c("3", 68, 975), c("로수바스타틴정", 200, 957), c("10mg", 340, 957), c("(Rosuvastatin)", 252, 995),
                c("1정", 480, 975), c("30일", 639, 975), c("-", 760, 975), c("-", 875, 975), c("1정", 991, 975),
                // 복약 지도와 마무리
                c("※", 52, 1097), c("복약", 90, 1097), c("지도", 135, 1097),
                c("-", 65, 1137), c("정해진", 110, 1137), c("시간에", 175, 1137), c("물과", 235, 1137), c("함께", 285, 1137), c("복용하세요.", 355, 1137),
                c("위와", 450, 1292), c("같이", 495, 1292), c("처방합니다.", 570, 1292),
                c("의료기관", 340, 1382), c(":", 400, 1382), c("○○○내과의원", 510, 1382)));
        return new OcrDocument(list, List.of(), List.of());
    }

    @Test
    @DisplayName("아침·점심·저녁 칸이 있는 처방전: 약 3개와 먹는 시간대, 용량, 투약일수를 읽는다")
    void readsTimeSlotGridFromWordPositions() {
        OcrResult r = PrescriptionTextParser.parse(sampleSlotGridPrescription(), TODAY);
        assertThat(r.items().size()).as("복약 지도와 환자 정보는 약이 아니다").isEqualTo(3);

        OcrResult.Item a = r.items().get(0);
        assertThat(a.drugName()).as("영문 병기는 뗀다").isEqualTo("아모디핀정 5mg");
        assertThat(dose(a)).isEqualTo("1.00");
        assertThat(a.timesPerDay()).isEqualTo((short) 2);
        assertThat(a.timingSlots().toString()).isEqualTo("[MORNING, DINNER]");
        assertThat(a.totalDays()).isEqualTo(30);
        assertThat(a.prn()).isFalse();

        OcrResult.Item b = r.items().get(1);
        assertThat(b.drugName()).isEqualTo("메토프로민정 500mg");
        assertThat(b.timesPerDay()).isEqualTo((short) 3);
        assertThat(b.timingSlots().toString()).isEqualTo("[MORNING, LUNCH, DINNER]");
        assertThat(b.totalDays()).isEqualTo(30);

        OcrResult.Item c = r.items().get(2);
        assertThat(c.drugName()).isEqualTo("로수바스타틴정 10mg");
        assertThat(c.timesPerDay()).isEqualTo((short) 1);
        assertThat(c.timingSlots().toString()).isEqualTo("[DINNER]");
        assertThat(c.totalDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("같은 처방전에서 병원과 처방일자도 읽는다 (환자 이름·생년월일은 결과에 없다)")
    void readsHospitalAndDateFromSlotGridSample() {
        OcrResult r = PrescriptionTextParser.parse(sampleSlotGridPrescription(), TODAY);
        assertThat(r.hospitalName().endsWith("내과의원")).as("병원: " + r.hospitalName()).isTrue();
        assertThat(r.prescribedDate()).as("1990-01-01(생년월일)이 아니라 처방일자").isEqualTo(LocalDate.of(2024, 5, 20));
        assertThat(r.pharmacyName() == null).isTrue();
        assertThat(r.toString().contains("홍길동")).as("환자 이름이 결과에 들어가면 안 된다").isFalse();
        assertThat(r.toString().contains("1990")).isFalse();
    }

    @Test
    @DisplayName("엔진이 표로 인식한 결과(합쳐진 머리글 두 줄)에서도 같은 값을 읽는다")
    void readsTimeSlotGridFromRecognizedTable() {
        OcrDocument.Table table = new OcrDocument.Table(List.of(
                List.of("No.", "약품명", "용량/規格", "투약일수", "복용법 (1일 3회)", "", ""),
                List.of("", "", "", "", "아침", "점심", "저녁"),
                List.of("1", "아모디핀정 5mg (Amlodipine)", "1정", "30일", "1정", "-", "1정"),
                List.of("2", "메토프로민정 500mg (Metformin)", "1정", "30일", "1정", "1정", "1정"),
                List.of("3", "로수바스타틴정 10mg (Rosuvastatin)", "1정", "30일", "-", "-", "1정"),
                List.of("※ 복약 지도", "", "", "", "", "", ""),
                List.of("4", "무시되는정", "1정", "1일", "1정", "1정", "1정")));
        OcrResult r = PrescriptionTextParser.parse(new OcrDocument(List.of(), List.of("처방일자 : 2024-05-20"), List.of(table)), TODAY);
        assertThat(r.items().size()).isEqualTo(3);
        assertThat(r.items().get(0).drugName()).isEqualTo("아모디핀정 5mg");
        assertThat(r.items().get(0).timingSlots().toString()).isEqualTo("[MORNING, DINNER]");
        assertThat(r.items().get(1).timingSlots().toString()).isEqualTo("[MORNING, LUNCH, DINNER]");
        assertThat(r.items().get(2).timingSlots().toString()).isEqualTo("[DINNER]");
        assertThat(r.items().get(2).totalDays()).isEqualTo(30);
        assertThat(dose(r.items().get(0))).isEqualTo("1.00");
        assertThat(r.prescribedDate()).isEqualTo(LocalDate.of(2024, 5, 20));
    }

    @Test
    @DisplayName("취침 칸이 있고 시간대마다 양이 다르면 용량 열의 값(없으면 첫 시간대의 값)을 1회 양으로 쓴다")
    void bedtimeAndPerSlotDoses() {
        // 용량 열이 없는 양식: 아침 2정, 저녁 1정, 취침 1정
        OcrDocument doc = ofWords(
                c("No", 60, 100), c("약품명", 250, 100), c("투약일수", 520, 100), c("아침", 700, 120), c("저녁", 850, 120), c("취침", 1000, 120),
                c("1", 60, 200), c("테스트정", 250, 200), c("14일", 520, 200), c("2정", 700, 200), c("1정", 850, 200), c("1정", 1000, 200),
                c("※", 60, 300), c("복약", 100, 300));
        OcrResult.Item item = PrescriptionTextParser.parse(doc, TODAY).items().get(0);
        assertThat(item.drugName()).isEqualTo("테스트정");
        assertThat(item.timingSlots().toString()).isEqualTo("[MORNING, DINNER, BEDTIME]");
        assertThat(item.timesPerDay()).isEqualTo((short) 3);
        assertThat(dose(item)).as("용량 열이 없으면 먹는 첫 시간대(아침)의 양").isEqualTo("2.00");
        assertThat(item.totalDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("번호(No.) 머리글이 없어도 맨 왼쪽의 번호로 행을 나눈다")
    void rowsWithoutNumberHeader() {
        OcrDocument doc = ofWords(
                c("약품명", 250, 100), c("용량", 480, 100), c("투약일수", 620, 100), c("아침", 760, 100), c("저녁", 900, 100),
                c("1", 60, 200), c("가정", 250, 200), c("1정", 480, 200), c("7일", 620, 200), c("1정", 760, 200), c("-", 900, 200),
                c("2", 60, 280), c("나정", 250, 280), c("1정", 480, 280), c("7일", 620, 280), c("-", 760, 280), c("1정", 900, 280),
                c("※", 60, 380));
        OcrResult r = PrescriptionTextParser.parse(doc, TODAY);
        assertThat(r.items().size()).isEqualTo(2);
        assertThat(r.items().get(0).timingSlots().toString()).isEqualTo("[MORNING]");
        assertThat(r.items().get(1).timingSlots().toString()).isEqualTo("[DINNER]");
    }

    @Test
    @DisplayName("시간대를 하나도 먹지 않는 줄은 횟수와 시간대를 비워 둔다")
    void rowWithNoMarkedSlot() {
        OcrDocument.Table table = new OcrDocument.Table(List.of(
                List.of("약품명", "투약일수", "아침", "점심", "저녁"),
                List.of("가정", "5일", "-", "-", "-")));
        OcrResult.Item item = PrescriptionTextParser.parse(new OcrDocument(List.of(), List.of(), List.of(table)), TODAY).items().get(0);
        assertThat(item.timesPerDay() == null).isTrue();
        assertThat(item.timingSlots().isEmpty()).isTrue();
        assertThat(item.totalDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("시간대 칸이 없는 일반 양식은 시간대를 비워 둔다 (프론트가 사용자에게 고르게 한다)")
    void regularLayoutHasNoTimingSlots() {
        OcrResult r = PrescriptionTextParser.parse(typicalPrescription(), TODAY);
        assertThat(r.items().size()).isEqualTo(4);
        for (OcrResult.Item item : r.items()) {
            assertThat(item.timingSlots().isEmpty()).as(item.drugName()).isTrue();
        }
    }

    @Test
    @DisplayName("시간대 머리글은 있는데 약품명 머리글이 없거나 번호가 없으면 아무것도 만들지 않는다")
    void incompleteSlotGridProducesNothing() {
        assertThat(PrescriptionTextParser.slotItemsFromWords(List.of(
                c("아침", 700, 100), c("점심", 800, 100), c("저녁", 900, 100), c("1정", 700, 200), c("1정", 800, 200),
                c("1정", 900, 200), c("가정", 300, 200), c("7일", 500, 200))).isEmpty()).as("약품명 머리글 없음").isTrue();
        assertThat(PrescriptionTextParser.slotItemsFromWords(List.of(
                c("약품명", 250, 100), c("아침", 700, 100), c("점심", 800, 100), c("저녁", 900, 100), c("가정", 250, 200),
                c("1정", 700, 200), c("1정", 800, 200), c("1정", 900, 200))).isEmpty()).as("행 번호 없음").isTrue();
        assertThat(PrescriptionTextParser.slotItemsFromWords(List.of()).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("이름표가 붙은 처방일은 5년 전까지, 이름표가 없는 날짜는 1년 전까지만 쓴다")
    void labeledDatesMayBeOlder() {
        assertThat(PrescriptionTextParser.findDate(List.of("처방일자 : 2024-05-20"), TODAY)).isEqualTo(LocalDate.of(2024, 5, 20));
        assertThat(PrescriptionTextParser.findDate(List.of("처방일자 : 2020-05-20"), TODAY) == null).as("5년보다 오래됨").isTrue();
        assertThat(PrescriptionTextParser.findDate(List.of("2024-05-20"), TODAY) == null).as("이름표 없는 오래된 날짜").isTrue();
        assertThat(PrescriptionTextParser.findDate(List.of("생년월일 : 1990-01-01", "처방일자 : 2024-05-20"), TODAY)).isEqualTo(LocalDate.of(2024, 5, 20));
    }

    @Test
    @DisplayName("약 이름의 영문 성분명 병기는 뗀다 (보험코드는 따로 뗀다)")
    void stripsLatinNotes() {
        assertThat(PrescriptionTextParser.cleanName("아모디핀정 5mg (Amlodipine)")).isEqualTo("아모디핀정 5mg");
        assertThat(PrescriptionTextParser.cleanName("[644900010] 타이레놀정 (Acetaminophen) 500mg")).isEqualTo("타이레놀정 500mg");
        assertThat(PrescriptionTextParser.cleanName("메토프로민정 500mg [Metformin HCl]")).isEqualTo("메토프로민정 500mg");
        assertThat(PrescriptionTextParser.cleanName("타이레놀정(500mg)")).as("숫자로 시작하는 괄호는 함량이라 남긴다").isEqualTo("타이레놀정(500mg)");
    }
}