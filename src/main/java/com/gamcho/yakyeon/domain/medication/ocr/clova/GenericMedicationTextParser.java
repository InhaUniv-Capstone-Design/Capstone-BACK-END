package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약봉투·복약안내문의 양식이 달라도 읽는 마지막 방법 (표 모양이나 문장 모양을 가정하지 않는다).
 *
 * 약국 조제 프로그램마다 약봉투를 다르게 찍어서, 정해진 표나 문장 모양을 기대하면 처음 보는 양식은 통째로 놓친다. 이 해석기는 줄 단위의 글만 보고:
 *  1) 약 이름을 찾는다: 정·캡슐·시럽·연고 등으로 끝나는 이름, 또는 함량(500mg) 앞의 이름
 *  2) 이름이 나온 줄과 그 아래 몇 줄에서 1회 투약량, 하루 횟수, 일수, 시간대, 식전·식후를 각각 따로 찾는다
 *  3) 약마다가 아니라 봉투 전체에 한 번 적힌 복용법("복용방법: 1일 3회 식후 30분")은 모든 약의 기본값으로 쓴다
 *  4) 못 찾은 값은 비워 두고 이름만이라도 돌려준다 (이름도 못 찾으면 빈 목록)
 * 이름은 약사법상 약봉투의 필수 기재사항이 아니라서 아예 없는 봉투도 있다. 그런 경우는 이 해석기로도 읽을 수 없다.
 *
 * 앞의 해석기들(표, 시간대 칸, 복용 문장)이 모두 약을 못 찾았을 때만 쓴다. 그래서 정확도가 높은 앞 방법이 있는 양식은 영향을 받지 않는다.
 */
final class GenericMedicationTextParser {

    private GenericMedicationTextParser() {
    }

    // ------------------------------------------------------------------ 약 이름

    private static final String STRENGTH = "\\d+(?:\\.\\d+)?\\s*(?:mg|㎎|mcg|μg|µg|ug|g|mL|ml|㎖|IU|%)";

    /** "아모디핀정 5mg", "타이레놀정500mg", "에스오메프라졸캡슐 20mg" (끝말이 약 모양이고 한글이 거기서 끝난다) */
    private static final Pattern SUFFIX_NAME = Pattern.compile(
            "([가-힣][가-힣A-Za-z0-9·\\-]{1,22}?(?:서방정|장용정|필름코팅정|구강붕해정|연질캡슐|캡슐|정|시럽|현탁액|건조시럽|과립|연고|크림|겔|패치|패취|좌약|점안액))(?![가-힣])"
                    + "(?:\\s?(" + STRENGTH + "))?");

    /** "새로운 보조약 100mg": 끝말은 없지만 함량이 붙은 이름 (앞의 한글 낱말 1~3개) */
    private static final Pattern STRENGTH_NAME = Pattern.compile("((?:[가-힣]{2,}\\s){0,2}[가-힣]{2,})\\s?(" + STRENGTH + ")");

    /** 약이 아닌 글: 조제·처방·환자·약국 안내 문구에 같은 끝말이 흔하다 */
    private static final Pattern NOT_A_DRUG = Pattern.compile(
            "환자|처방|조제|복용|투약|약국|병원|의원|약사|발행|교부|보험|본인|부담|금액|총액|공단|전화|주소|사업자|면허|성명|"
                    + "용법|용량|일수|횟수|효능|주의|보관|유효|사용기한|일정|예정|확정|결정|수정|조정|지정|설정|규정|안정|인정|판정|특정|고정|보정");

    private static final Set<String> HEADER_WORDS = Set.of("약품명", "약품", "의약품", "약명", "제품명");

    // ------------------------------------------------------------------ 복용 정보

    private static final String UNIT_SOLID = "정|캡슐|캡|포|개|알";
    private static final String UNIT_ANY = UNIT_SOLID + "|mL|ml|g";
    private static final String DOSE_NUM = "\\d{1,2}(?:[./]\\d{1,2})?|½|반";

    /** "1회 1정", "1회 투약량 2정" */
    private static final Pattern DOSE_PER_TIME = Pattern.compile("1\\s*회\\s*(?:투약량|복용량|용량)?\\s*[:：]?\\s*(" + DOSE_NUM + ")\\s*(" + UNIT_ANY + ")");
    /** "1정씩", "1정식" */
    private static final Pattern DOSE_EACH = Pattern.compile("(" + DOSE_NUM + ")\\s*(" + UNIT_ANY + ")\\s*(?:씩|식)");
    /** "1T", "2C": 약국에서 정(Tablet)·캡슐(Capsule)을 줄여 쓴다 */
    private static final Pattern DOSE_ABBREVIATED = Pattern.compile("(?<![A-Za-z0-9.])(" + DOSE_NUM + ")\\s*[TtCc](?![A-Za-z])");
    /** 그냥 "2정" (총 개수와 헷갈리지 않게 값이 작을 때만 인정) */
    private static final Pattern DOSE_BARE = Pattern.compile("(?<![0-9.])(" + DOSE_NUM + ")\\s*(" + UNIT_SOLID + ")(?![가-힣])");

    /** "1일 3회", "하루 세 번", "매일 2회" */
    private static final Pattern TIMES_DAILY = Pattern.compile("(?:1\\s*일|하루|매일)\\s*([1-4]|한|두|세|네)\\s*(?:회|번)");
    /** "2회" (단, "1회 1정"의 1회, "1회 투약량"은 제외) */
    private static final Pattern TIMES_PLAIN = Pattern.compile(
            "(?<![0-9])([1-4])\\s*회(?!\\s*(?:투약|복용량|용량))(?!\\s*[0-9½반]\\s*(?:" + UNIT_ANY + "))");
    private static final Pattern TIMES_BUN = Pattern.compile("(?<![0-9])([1-4])\\s*번\\s*(?:복용|씩|드)");

    private static final Pattern DAYS_BUN = Pattern.compile("(?<![0-9])(\\d{1,3})\\s*일\\s*(?:분|치|간|동안)");
    /** "투약일수 : 7", "일수 7일": 이름표가 일수라고 말해 주므로 숫자만 있어도 된다 */
    private static final Pattern DAYS_LABELED = Pattern.compile("(?:투약\\s*일수|일수)\\s*[:：]?\\s*(\\d{1,3})\\s*일?");
    /** "총 7일": "총 30정"(총 개수)을 30일로 읽지 않도록 "일"이 꼭 있어야 한다 */
    private static final Pattern DAYS_TOTAL = Pattern.compile("총\\s*(\\d{1,3})\\s*일");
    /** "7일"만 적힌 경우(2일 이상): "1일 3회"의 1일과 구분하려고 뒤에 횟수가 오는 경우는 제외 */
    private static final Pattern DAYS_BARE = Pattern.compile("(?<![0-9])([2-9]|[1-9]\\d{1,2})\\s*일(?!\\s*(?:분|치|간|[0-9한두세네])\\s*(?:회|번)?)(?![가-힣])");

    private static final Pattern SLOT_WORD = Pattern.compile("아침|점심|저녁|취침|자기\\s*전");
    private static final Pattern EVERY_MEAL = Pattern.compile("매\\s*식\\s*(?:후|전|사)|식\\s*사\\s*마다|매\\s*끼");
    private static final Pattern FOOTER = Pattern.compile("복약\\s*지도|※|문의|전화|TEL|tel|약국|조제\\s*약사|조제자");

    private static final int MAX_FOLLOWING_ROWS = 4;
    private static final int MAX_ITEMS = 12;

    // ------------------------------------------------------------------ 진입점

    /**
     * 줄마다 글을 이어 붙이되, 그 줄에서 가장 큰 글자의 0.72배보다 작은 글자는 뺀다.
     * 약봉투의 주의사항 아이콘 밑에는 "식후 즉시", "저녁 복용" 같은 작은 글이 같은 줄에 있어서, 그대로 읽으면 옆 약의 복용 정보로 새어 들어온다.
     */
    static List<String> rowTexts(List<WordRows.Row> rows) {
        List<String> texts = new ArrayList<>();
        for (WordRows.Row row : rows) {
            List<OcrDocument.Word> ws = new ArrayList<>(row.words());
            ws.sort((a, b) -> Double.compare(a.left(), b.left()));
            double maxHeight = ws.stream().mapToDouble(OcrDocument.Word::height).max().orElse(0);
            StringBuilder sb = new StringBuilder();
            for (OcrDocument.Word w : ws) {
                if (w.height() >= 0.72 * maxHeight) {
                    sb.append(w.text()).append(' ');
                }
            }
            texts.add(sb.toString().trim());
        }
        return texts;
    }

    static List<OcrResult.Item> items(List<String> rows) {
        List<String> lines = new ArrayList<>();
        for (String r : rows) {
            if (r != null && !r.isBlank()) {
                lines.add(r.trim());
            }
        }
        if (lines.isEmpty()) {
            return List.of();
        }

        // 1) 약 이름이 있는 줄
        List<List<NameHit>> hits = new ArrayList<>();
        int firstNameRow = -1;
        for (int i = 0; i < lines.size(); i++) {
            List<NameHit> found = findNames(lines.get(i));
            hits.add(found);
            if (!found.isEmpty() && firstNameRow < 0) {
                firstNameRow = i;
            }
        }
        if (firstNameRow < 0) {
            return List.of();
        }

        // 2) 약마다의 글(이름 뒤 같은 줄 + 아래 줄들)과, 어느 약에도 속하지 않은 줄(봉투 전체 복용법)을 가른다
        boolean[] consumed = new boolean[lines.size()];
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            List<NameHit> found = hits.get(i);
            for (int k = 0; k < found.size(); k++) {
                NameHit hit = found.get(k);
                int endOfBlockOnLine = k + 1 < found.size() ? found.get(k + 1).start : lines.get(i).length();
                StringBuilder block = new StringBuilder(lines.get(i).substring(hit.end, endOfBlockOnLine));
                consumed[i] = true;
                if (k == found.size() - 1) {
                    for (int j = i + 1; j < lines.size() && j <= i + MAX_FOLLOWING_ROWS; j++) {
                        if (!hits.get(j).isEmpty() || FOOTER.matcher(lines.get(j)).find()) {
                            break;
                        }
                        block.append(' ').append(lines.get(j));
                        consumed[j] = true;
                    }
                }
                candidates.add(new Candidate(hit.name, hit.strength, block.toString()));
            }
        }
        StringBuilder common = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (!consumed[i]) {
                common.append(lines.get(i)).append(' ');
            }
        }
        Facts global = Facts.from(common.toString());

        // 3) 약 한 줄씩 만들기 (약마다의 값 > 봉투 전체 값 > 비워 둠)
        Map<String, OcrResult.Item> byName = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            if (byName.size() >= MAX_ITEMS) {
                break;
            }
            OcrResult.Item item = toItem(c, Facts.from(c.block), global);
            if (item != null) {
                byName.putIfAbsent(item.drugName(), item);
            }
        }
        return new ArrayList<>(byName.values());
    }

    // ------------------------------------------------------------------ 이름 찾기

    private record NameHit(String name, String strength, int start, int end) {
    }

    private record Candidate(String name, String strength, String block) {
    }

    private static List<NameHit> findNames(String line) {
        List<NameHit> hits = new ArrayList<>();
        boolean[] taken = new boolean[line.length()];
        Matcher m = SUFFIX_NAME.matcher(line);
        while (m.find()) {
            String name = m.group(1);
            if (!isDrugName(name)) {
                continue;
            }
            hits.add(new NameHit(name, m.group(2), m.start(), m.end()));
            for (int p = m.start(); p < m.end(); p++) {
                taken[p] = true;
            }
        }
        Matcher s = STRENGTH_NAME.matcher(line);
        while (s.find()) {
            if (taken[s.start()] || taken[s.start(2) - 1 < 0 ? 0 : s.start(2) - 1]) {
                continue;   // 이미 끝말 이름으로 잡힌 자리
            }
            String name = s.group(1).trim();
            if (isDrugName(name) && !HEADER_WORDS.contains(name.replaceAll("\\s+", ""))) {
                hits.add(new NameHit(name, s.group(2), s.start(), s.end()));
            }
        }
        hits.sort((a, b) -> Integer.compare(a.start, b.start));
        return hits;
    }

    private static boolean isDrugName(String name) {
        if (name.replaceAll("[^가-힣]", "").length() < 2 || NOT_A_DRUG.matcher(name).find()) {
            return false;
        }
        String cleaned = PrescriptionTextParser.cleanName(name);
        return cleaned != null && cleaned.length() >= 2;
    }

    // ------------------------------------------------------------------ 복용 정보 읽기

    private record Facts(BigDecimal dose, Integer times, Integer days, List<String> slots, boolean everyMeal,
                         PrescriptionTextParser.Usage usage) {

        static Facts from(String text) {
            String t = text == null ? "" : text;
            return new Facts(readDose(t), readTimes(t), readDays(t), readSlots(t), EVERY_MEAL.matcher(t).find(), PrescriptionTextParser.parseUsage(t));
        }
    }

    private static BigDecimal readDose(String t) {
        for (Pattern p : List.of(DOSE_PER_TIME, DOSE_EACH, DOSE_ABBREVIATED, DOSE_BARE)) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                BigDecimal d = PrescriptionTextParser.parseDose(m.group(1));
                boolean solid = m.groupCount() < 2 || m.group(2).matches(UNIT_SOLID);
                if (d != null && d.signum() > 0 && d.compareTo(BigDecimal.valueOf(solid ? 6 : 30)) <= 0) {
                    return d;   // 총 개수("30정")를 1회량으로 읽지 않게 범위를 둔다
                }
            }
        }
        return null;
    }

    private static Integer readTimes(String t) {
        for (Pattern p : List.of(TIMES_DAILY, TIMES_PLAIN, TIMES_BUN)) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                return koreanNumber(m.group(1));
            }
        }
        return null;
    }

    private static Integer koreanNumber(String s) {
        return switch (s) {
            case "한" -> 1;
            case "두" -> 2;
            case "세" -> 3;
            case "네" -> 4;
            default -> Integer.parseInt(s);
        };
    }

    private static Integer readDays(String t) {
        for (Pattern p : List.of(DAYS_BUN, DAYS_TOTAL, DAYS_LABELED, DAYS_BARE)) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 365) {
                    return n;
                }
            }
        }
        return null;
    }

    private static List<String> readSlots(String t) {
        List<String> found = new ArrayList<>();
        Matcher m = SLOT_WORD.matcher(t);
        while (m.find()) {
            String slot = switch (m.group().replaceAll("\\s+", "")) {
                case "아침" -> "MORNING";
                case "점심" -> "LUNCH";
                case "저녁" -> "DINNER";
                default -> "BEDTIME";
            };
            if (!found.contains(slot)) {
                found.add(slot);
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String s : List.of("MORNING", "LUNCH", "DINNER", "BEDTIME")) {
            if (found.contains(s)) {
                ordered.add(s);
            }
        }
        return ordered;
    }

    // ------------------------------------------------------------------ 약 한 줄 합치기

    private static OcrResult.Item toItem(Candidate c, Facts own, Facts global) {
        String name = PrescriptionTextParser.cleanName(c.strength == null ? c.name : c.name + " " + c.strength.replaceAll("\\s+", ""));
        if (name == null) {
            return null;
        }
        BigDecimal dose = own.dose != null ? own.dose : global.dose;
        Integer times = own.times != null ? own.times : global.times;
        Integer days = own.days != null ? own.days : global.days;
        List<String> slots = !own.slots.isEmpty() ? own.slots : global.slots;
        PrescriptionTextParser.Usage usage = own.usage.meal() != null || own.usage.prn() ? own.usage : mergeUsage(own.usage, global.usage);
        boolean prn = own.usage.prn();
        boolean everyMeal = own.everyMeal || (own.slots.isEmpty() && global.everyMeal);

        if (slots.isEmpty() && everyMeal && times != null && times >= 2 && times <= 4) {
            slots = defaultSlots(times);   // "매 식후"는 끼니마다: 하루 횟수만큼 아침·점심·저녁(·취침)
        }
        if (times == null && !slots.isEmpty()) {
            times = slots.size();
        }
        if (times != null && !slots.isEmpty() && slots.size() != times) {
            slots = List.of();   // 시간대 개수와 횟수가 어긋나면 틀리게 알려 주느니 사용자가 고르게 둔다
        }
        if (prn) {
            times = null;
            slots = List.of();
        }
        return new OcrResult.Item(name, dose, times == null ? null : times.shortValue(), days, prn, usage.meal(), usage.offset(), slots);
    }

    private static PrescriptionTextParser.Usage mergeUsage(PrescriptionTextParser.Usage own, PrescriptionTextParser.Usage global) {
        return global.meal() != null ? new PrescriptionTextParser.Usage(false, global.meal(), global.offset()) : own;
    }

    private static List<String> defaultSlots(int times) {
        return switch (times) {
            case 2 -> List.of("MORNING", "DINNER");
            case 3 -> List.of("MORNING", "LUNCH", "DINNER");
            case 4 -> List.of("MORNING", "LUNCH", "DINNER", "BEDTIME");
            default -> List.of();
        };
    }
}