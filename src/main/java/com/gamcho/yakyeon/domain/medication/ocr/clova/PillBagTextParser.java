package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약국이 조제하면서 주는 복약안내문·약봉투 양식(복용법이 "1정식 2회 30일분" 같은 문장으로 적힌 양식)을 글자 위치로 읽는다.
 *
 * 처방전과 다른 점: 1회 투약량·횟수·일수가 따로 떨어진 칸이 아니라 한 문장이고, 시간대(아침·저녁)도 머리글이 아니라 문장 안의 글자다.
 * 표 인식을 켜지 않아도 읽도록 글자 조각의 위치만 쓴다.
 *
 * 읽는 순서:
 *  1) 복용 문장("1정식 2회 30일분")을 한 줄에서 찾아 약 한 줄의 기준(anchor)으로 삼는다.
 *  2) 기준 문장과 같은 왼쪽 위치에서 시작하는 줄을 아래로 모은다 (둘째 줄의 "아침 1정, 저녁 1정"). 오른쪽 칸의 주의사항 문구는 시작 위치가
 *     달라서 들어오지 않는다.
 *  3) 약 이름은 이름 칸(글자의 왼쪽 시작 위치가 모이는 곳으로 효능 설명 칸과 나눈다)에서 읽는다.
 * 약을 하나도 못 찾으면 빈 목록이고, 그때는 호출한 쪽이 기존 처방전 해석으로 넘어간다.
 */
final class PillBagTextParser {

    private PillBagTextParser() {
    }

    // ------------------------------------------------------------------ 복용 문장

    private static final String DOSE = "(?<dose>\\d{1,2}(?:[./]\\d{1,2})?|½)";
    private static final String UNIT = "(?:정|캡슐|캡|포|개|알|매|mL|ml|g)";
    private static final String SEP = "[\\s,/·]*";

    /** "1정식 2회 30일분" ("식"은 "씩"의 오인식으로도 나온다) */
    private static final Pattern PER_DOSE_FIRST = Pattern.compile(
            "^" + DOSE + "\\s*" + UNIT + "\\s*(?:식|씩)?" + SEP + "(?<times>\\d)\\s*(?:회|번)" + SEP + "(?<days>\\d{1,3})\\s*일\\s*분?");
    /** "1회 1정, 1일 3회, 7일분" */
    private static final Pattern ONCE_FIRST = Pattern.compile(
            "^1\\s*회\\s*" + DOSE + "\\s*" + UNIT + "\\s*(?:식|씩)?" + SEP + "(?:1\\s*일|하루)\\s*(?<times>\\d)\\s*(?:회|번)" + SEP
                    + "(?<days>\\d{1,3})\\s*일\\s*분?");
    /** "1일 3회 1회 1정 7일분" */
    private static final Pattern DAILY_FIRST = Pattern.compile(
            "^(?:1\\s*일|하루)\\s*(?<times>\\d)\\s*(?:회|번)" + SEP + "(?:1\\s*회\\s*)?" + DOSE + "\\s*" + UNIT + "\\s*(?:식|씩)?" + SEP
                    + "(?<days>\\d{1,3})\\s*일\\s*분?");
    private static final List<Pattern> SENTENCES = List.of(PER_DOSE_FIRST, ONCE_FIRST, DAILY_FIRST);

    private static final Pattern EVERY_MEAL = Pattern.compile("매\\s*식\\s*(?:후|전|사)|식\\s*사\\s*마다");
    private static final Pattern SLOT_WORD = Pattern.compile("아침|점심|저녁|취침|자기\\s*전");

    /** 표 아래에 나오는 안내 문구: 마지막 약 행의 끝을 정한다 */
    private static final List<String> FOOTER_KEYS = List.of("복약지도", "위와같이", "처방합니다", "※", "약봉투내용", "조제시", "조제기간");

    private record Anchor(OcrDocument.Word start, BigDecimal dose, short times, int days, String sentenceEnd) {
    }

    // ------------------------------------------------------------------ 진입점

    static List<OcrResult.Item> itemsFromWords(List<OcrDocument.Word> words) {
        if (words == null || words.size() < 6) {
            return List.of();
        }
        double h = WordRows.medianHeight(words);
        // 같은 문장으로 볼 글자 사이 간격: 글자 높이의 0.9배까지. 문장 안의 띄어쓰기는 3~4px이고 옆 칸(주의사항 아이콘 문구 등)은 30px 이상 떨어져 있다
        double gapMax = Math.max(14, 0.9 * h);
        List<WordRows.Row> rows = WordRows.group(words);

        List<Anchor> anchors = findAnchors(rows, gapMax);
        if (anchors.isEmpty()) {
            return List.of();
        }
        anchors.sort(Comparator.comparingDouble(a -> a.start().top()));

        double doseLeft = anchors.stream().mapToDouble(a -> a.start().left()).min().orElse(0);
        double tolX = Math.max(12, 0.7 * h);
        double footerTop = footerTop(rows, anchors.get(anchors.size() - 1).start().top());
        double[] nameColumn = nameColumn(words, rows, anchors, doseLeft, h, footerTop);

        List<OcrResult.Item> items = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Anchor anchor = anchors.get(i);
            double bandTop = anchor.start().top() - 0.9 * h;
            double bandBottom = i + 1 < anchors.size() ? anchors.get(i + 1).start().top() - 0.9 * h : footerTop;
            OcrResult.Item item = buildItem(words, anchor, bandTop, bandBottom, nameColumn, doseLeft, tolX, gapMax, h);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    // ------------------------------------------------------------------ 1) 기준 문장 찾기

    private static List<Anchor> findAnchors(List<WordRows.Row> rows, double gapMax) {
        List<Anchor> anchors = new ArrayList<>();
        for (WordRows.Row row : rows) {
            List<OcrDocument.Word> ws = new ArrayList<>(row.words());
            ws.sort(Comparator.comparingDouble(OcrDocument.Word::left));
            for (int j = 0; j < ws.size(); j++) {
                String segment = segmentFrom(ws, j, gapMax);
                Anchor anchor = matchAnchor(ws.get(j), segment);
                if (anchor != null) {
                    anchors.add(anchor);
                    break;   // 한 줄에서는 가장 왼쪽의 문장 하나만
                }
            }
        }
        return anchors;
    }

    /**
     * j번째 조각부터 간격이 벌어지지 않는 조각들을 공백으로 이은 글 (옆 칸의 글자가 이어지지 않게).
     * 시작 글자보다 눈에 띄게 작은 글자(높이 0.72배 미만, 주의사항 아이콘 밑의 작은 설명)도 같은 줄에 있어도 이어 붙이지 않는다.
     */
    private static String segmentFrom(List<OcrDocument.Word> ws, int j, double gapMax) {
        StringBuilder sb = new StringBuilder(ws.get(j).text());
        double minHeight = 0.72 * ws.get(j).height();
        for (int k = j + 1; k < ws.size() && ws.get(k).left() - ws.get(k - 1).right() <= gapMax && ws.get(k).height() >= minHeight; k++) {
            sb.append(' ').append(ws.get(k).text());
        }
        return sb.toString();
    }

    private static Anchor matchAnchor(OcrDocument.Word start, String segment) {
        for (Pattern p : SENTENCES) {
            Matcher m = p.matcher(segment);
            if (m.lookingAt()) {
                BigDecimal dose = PrescriptionTextParser.parseDose(m.group("dose"));
                int times = Integer.parseInt(m.group("times"));
                int days = Integer.parseInt(m.group("days"));
                if (dose != null && times >= 1 && times <= 4 && days >= 1 && days <= 365) {
                    return new Anchor(start, dose, (short) times, days, segment.substring(m.end()));
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 2) 표의 끝

    private static double footerTop(List<WordRows.Row> rows, double lastAnchorTop) {
        double top = Double.MAX_VALUE;
        for (WordRows.Row row : rows) {
            double rowTop = row.words().stream().mapToDouble(OcrDocument.Word::top).min().orElse(Double.MAX_VALUE);
            if (rowTop <= lastAnchorTop + 1) {
                continue;
            }
            String compact = row.text().replaceAll("\\s+", "");
            if (FOOTER_KEYS.stream().anyMatch(compact::contains)) {
                top = Math.min(top, rowTop);
            }
        }
        // 안내 문구가 없으면 마지막 약 행 아래로 넉넉히 (약 이름이 두세 줄로 접혀도 들어오게)
        return top == Double.MAX_VALUE ? lastAnchorTop + 120 : top - 4;
    }

    // ------------------------------------------------------------------ 3) 약 이름 칸

    /**
     * 이름 칸의 [왼쪽, 오른쪽) 범위. 약 행들의 글자가 시작하는 왼쪽 위치(x)를 모아서(7칸 이내는 같은 곳) 3개 이상 모인 곳을 칸의 시작으로 본다.
     * 머리글 "약품명"에서 가까운 첫 칸이 이름 칸이고, 그 오른쪽의 다음 칸(효능 설명)의 시작이 이름 칸의 끝이다.
     */
    private static double[] nameColumn(List<OcrDocument.Word> words, List<WordRows.Row> rows, List<Anchor> anchors,
                                       double doseLeft, double h, double footerTop) {
        double firstAnchorTop = anchors.get(0).start().top();
        OcrDocument.Word header = nameHeader(words, firstAnchorTop);

        List<Double> lefts = new ArrayList<>();
        for (OcrDocument.Word w : words) {
            if (w.top() >= firstAnchorTop - 0.9 * h && w.top() < footerTop && w.right() <= doseLeft - 4) {
                lefts.add(w.left());
            }
        }
        lefts.sort(Double::compare);
        List<double[]> clusters = new ArrayList<>();   // {시작(가장 작은 left), 개수}
        int i = 0;
        while (i < lefts.size()) {
            int j = i;
            while (j + 1 < lefts.size() && lefts.get(j + 1) - lefts.get(j) <= 7) {
                j++;
            }
            if (j - i + 1 >= Math.max(3, anchors.size())) {
                clusters.add(new double[]{lefts.get(i), j - i + 1});
            }
            i = j + 1;
        }

        double nameLeft = header != null ? header.left() - 130 : -1;
        // 머리글 "약품명"의 왼쪽 위치에 가장 가까운 칸이 이름 칸이다 (그 왼쪽의 번호 칸을 이름 칸으로 잘못 고르지 않게).
        // 머리글이 없으면 가장 왼쪽 칸
        double start = Double.NaN;
        double bestDistance = Double.MAX_VALUE;
        for (double[] c : clusters) {
            boolean nearHeader = header == null || (c[0] >= header.left() - 130 && c[0] <= header.right());
            if (nearHeader) {
                double distance = header == null ? c[0] : Math.abs(c[0] - header.left());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    start = c[0];
                }
            }
        }
        double next = Double.NaN;
        if (!Double.isNaN(start)) {
            for (double[] c : clusters) {
                if (c[0] > start + 80) {
                    next = c[0];
                    break;
                }
            }
        }
        double left = Double.isNaN(start) ? nameLeft : start - 12;
        double right = Double.isNaN(next) ? doseLeft - 15 : next - 4;
        return new double[]{left, right};
    }

    /** 머리글의 "약품명": 첫 약 행보다 위에 있는 것 */
    private static OcrDocument.Word nameHeader(List<OcrDocument.Word> words, double firstAnchorTop) {
        OcrDocument.Word best = null;
        for (OcrDocument.Word w : words) {
            String compact = w.text().replaceAll("\\s+", "");
            if (w.top() < firstAnchorTop && (compact.contains("약품명") || compact.equals("약명") || compact.contains("제품명"))) {
                if (best == null || w.top() > best.top()) {
                    best = w;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ 4) 약 한 줄 만들기

    private static OcrResult.Item buildItem(List<OcrDocument.Word> words, Anchor anchor, double bandTop, double bandBottom,
                                            double[] nameColumn, double doseLeft, double tolX, double gapMax, double h) {
        // 이름: 이름 칸 안의 글자를 위에서 아래로, 왼쪽에서 오른쪽으로
        List<OcrDocument.Word> nameWords = new ArrayList<>();
        for (OcrDocument.Word w : words) {
            if (w.top() >= bandTop && w.top() < bandBottom && w.left() >= nameColumn[0] && w.left() < nameColumn[1]) {
                nameWords.add(w);
            }
        }
        StringBuilder name = new StringBuilder();
        for (WordRows.Row r : WordRows.group(nameWords)) {
            List<OcrDocument.Word> ws = new ArrayList<>(r.words());
            ws.sort(Comparator.comparingDouble(OcrDocument.Word::left));
            for (OcrDocument.Word w : ws) {
                name.append(w.text()).append(' ');
            }
        }
        String drugName = PrescriptionTextParser.cleanName(name.toString());
        if (drugName == null) {
            return null;
        }

        // 복용 칸: 기준 문장과 같은 위치에서 시작하는 줄들
        List<OcrDocument.Word> doseWords = new ArrayList<>();
        for (OcrDocument.Word w : words) {
            if (w.top() >= bandTop && w.top() < bandBottom && w.left() >= anchor.start().left() - tolX) {
                doseWords.add(w);
            }
        }
        StringBuilder block = new StringBuilder();
        for (WordRows.Row r : WordRows.group(doseWords)) {
            List<OcrDocument.Word> ws = new ArrayList<>(r.words());
            ws.sort(Comparator.comparingDouble(OcrDocument.Word::left));
            if (ws.get(0).left() > anchor.start().left() + 2 * tolX) {
                continue;   // 시작 위치가 다른 줄(오른쪽 주의사항 칸의 문구)
            }
            block.append(segmentFrom(ws, 0, gapMax)).append(' ');
        }
        String rest = anchor.sentenceEnd();
        String blockText = block.toString().trim();
        // 문장 뒤(아래 줄 포함)의 글: 시간대와 식사 관계
        Matcher sentence = firstSentence(blockText);
        if (sentence != null) {
            rest = blockText.substring(sentence.end());
        }

        List<String> slots = new ArrayList<>();
        Matcher sm = SLOT_WORD.matcher(rest);
        while (sm.find()) {
            String slot = slotOf(sm.group());
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String s : List.of("MORNING", "LUNCH", "DINNER", "BEDTIME")) {
            if (slots.contains(s)) {
                ordered.add(s);
            }
        }
        if (ordered.isEmpty() && EVERY_MEAL.matcher(rest).find()) {
            ordered = defaultSlots(anchor.times());   // "매 식후"는 끼니마다: 하루 횟수만큼 아침·점심·저녁(·취침)
        }
        if (ordered.size() != anchor.times()) {
            ordered = List.of();   // 시간대 개수와 횟수가 어긋나면 틀리게 알려 주느니 사용자가 고르게 둔다
        }

        PrescriptionTextParser.Usage usage = PrescriptionTextParser.parseUsage(rest);
        if (usage.prn()) {
            ordered = List.of();   // DB 규칙: 필요 시 복용 약은 시간대를 갖지 않는다
        }
        PatientMedication.MealRelation meal = usage.meal();
        Short offset = usage.offset();
        Short times = usage.prn() ? null : anchor.times();
        return new OcrResult.Item(drugName, anchor.dose(), times, anchor.days(), usage.prn(), meal, offset, ordered);
    }

    private static Matcher firstSentence(String text) {
        for (Pattern p : SENTENCES) {
            Matcher m = p.matcher(text);
            if (m.lookingAt()) {
                return m;
            }
        }
        return null;
    }

    private static String slotOf(String word) {
        String w = word.replaceAll("\\s+", "");
        return switch (w) {
            case "아침" -> "MORNING";
            case "점심" -> "LUNCH";
            case "저녁" -> "DINNER";
            default -> "BEDTIME";
        };
    }

    private static List<String> defaultSlots(short times) {
        return switch (times) {
            case 2 -> List.of("MORNING", "DINNER");
            case 3 -> List.of("MORNING", "LUNCH", "DINNER");
            case 4 -> List.of("MORNING", "LUNCH", "DINNER", "BEDTIME");
            default -> List.of();
        };
    }
}