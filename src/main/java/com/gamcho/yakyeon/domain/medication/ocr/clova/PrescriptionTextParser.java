package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR이 읽은 글자(OcrDocument)에서 처방전 내용을 뽑아 OcrResult로 만든다 (스프링·DB 없는 순수 로직).
 *
 * 우리나라 처방전의 표는 "처방 의약품의 명칭 | 1회 투약량 | 1일 투여횟수 | 총 투약일수 | 용법" 순서다.
 * 처방전 양식이 둘 이상이라 양식별로 아래 순서로 약을 찾는다. 먼저 나온 방법에서 약이 하나라도 나오면 거기서 멈춘다.
 *   [시간대 칸 양식] "약품명 | 용량 | 투약일수 | 복용법(아침 | 점심 | 저녁)": 칸마다 "1정" 또는 "-"가 있어서 약을 먹는 시간대를 그대로 읽는다.
 *   1) 엔진이 표로 인식한 영역(tables)에서 시간대 칸 양식
 *   2) 엔진이 표로 인식한 영역(tables)에서 일반 양식: 머리글 칸 이름으로 열을 찾고, 그 아래 행을 읽는다.
 *   3) 글자 위치(words)로 읽는 시간대 칸 양식: 아침·점심·저녁 글자의 가로 위치로 열을 정하고, 약 번호(No.)의 세로 위치로 행을 정한다.
 *   4) 글자 위치로 묶은 행(words)에서 일반 양식: 머리글 행을 찾고, 그 아래 행에서 "이름 숫자 숫자 숫자 용법" 모양을 읽는다.
 *      엔진이 알려 준 줄(lines)만 있을 때도 같은 방식이다.
 *
 * 읽지 못한 값은 null이다. 사용자가 초안을 확인하고 고친 뒤 등록하므로, 확실하지 않은 값을 억지로 채우지 않는다.
 * 약 이름은 글자 그대로의 원문이고, 약물 DB 매칭은 서비스가 한다.
 * 환자 이름·주민등록번호는 읽지도 반환하지도 않는다(필요한 것은 병원, 약국, 날짜, 약뿐이다).
 *
 * 주의: 처방전 양식은 병원마다 조금씩 다르다. 실제 처방전 사진으로 시험하면서 키워드와 규칙을 보강해야 한다.
 */
public final class PrescriptionTextParser {

    // 긴 접미사를 먼저 둔다 (치과의원이 "치과"로 잘리지 않게)
    private static final Pattern HOSPITAL = Pattern.compile(
            "([가-힣A-Za-z0-9]{1,20}?(?:치과의원|치과병원|한의원|병원|의원|의료원|클리닉|치과))");
    private static final Pattern PHARMACY = Pattern.compile("([가-힣A-Za-z0-9]{1,20}?약국)");
    private static final Pattern DATE = Pattern.compile(
            "(20\\d{2})\\s*[년.\\-/]\\s*(\\d{1,2})\\s*[월.\\-/]\\s*(\\d{1,2})");

    /** 약 한 줄: 이름 + 1회 투약량 + 1일 횟수 + 총 일수 + 나머지(용법). 이름 안의 함량(500mg 등)이 숫자 칸으로 오인되지 않게 숫자는 한두 자리로 제한 */
    private static final Pattern ROW = Pattern.compile(
            "^(?<name>.*?[가-힣A-Za-z].*?)\\s+(?<dose>\\d{1,2}(?:[./]\\d{1,2})?|½)\\s*(?:정|캡슐|캡|포|개|알|매|mL|ml|g)?"
                    + "\\s+(?<times>\\d)\\s*회?\\s+(?<days>\\d{1,3})\\s*(?:일분|일)?(?<rest>(?:\\s.*)?)$");

    /** 괄호 안이 영문으로 시작하는 병기: "(Amlodipine)", "[Metformin HCl]" (보험코드 "[644900010]"은 숫자로 시작해서 해당 없음) */
    private static final Pattern LATIN_NOTE = Pattern.compile("\\s*[(\\[][A-Za-z][A-Za-z0-9\\s.,'/\\-]*[)\\]]");
    private static final Pattern LEADING_CODE = Pattern.compile("^[\\[(]?\\d{6,12}[\\])]?\\s*");
    /** 숫자·괄호·함량 단위·제형 글자만 지우면 아무것도 남지 않는 이름(예: "500 mg", "1/2", "정")은 약 이름이 아니다 */
    private static final Pattern NAME_NOISE = Pattern.compile("[0-9.,/\\s()\\[\\]\\-]|mg|㎎|ml|mL|㎖|g|정|캡슐|포|개|매");
    /** 머리글 없이 숫자 모양만으로 약을 찾을 때, 약 이름처럼 보이는 것만 인정한다 */
    private static final Pattern DRUG_LIKE = Pattern.compile(".*(정|캡슐|시럽|현탁액|액|산|환|크림|연고|겔|패치|좌약|mg|㎎|ml|mL).*");

    private static final List<String> NAME_KEYS = List.of("명칭", "의약품", "약품명", "약명");
    private static final List<String> DOSE_KEYS = List.of("투약량", "1회");
    private static final List<String> TIMES_KEYS = List.of("횟수", "투여");
    private static final List<String> DAYS_KEYS = List.of("일수", "투약일");
    private static final List<String> USAGE_KEYS = List.of("용법");
    private static final List<String> FOOTER_KEYS = List.of("주사제", "조제시", "처방의변경", "조제내역", "조제기간", "조제일자",
            "조제약사", "※", "의약품관리", "약국");

    private static final Pattern PRN = Pattern.compile("필요\\s*시|필요\\s*할\\s*때|증상\\s*시|통증\\s*시|열\\s*날\\s*때|PRN|prn");
    private static final Pattern MEAL_OFFSET = Pattern.compile("(식\\s*후|식\\s*전)\\s*(\\d{1,3})\\s*분");

    private PrescriptionTextParser() {
    }

    public static OcrResult parse(OcrDocument doc, LocalDate today) {
        List<WordRows.Row> rows = WordRows.group(doc.words());
        List<String> rowTexts = rows.isEmpty() ? doc.lines().stream().map(PrescriptionTextParser::tidy).toList()
                : rows.stream().map(r -> tidy(r.text())).toList();

        // 병원·약국·날짜는 엔진의 줄과 위치로 묶은 행을 모두 훑는다 (한쪽이 글자를 다르게 묶어도 찾도록)
        List<String> allTexts = new ArrayList<>(doc.lines().stream().map(PrescriptionTextParser::tidy).toList());
        for (String text : rowTexts) {
            if (!allTexts.contains(text)) {
                allTexts.add(text);
            }
        }

        // 시간대 칸 양식을 먼저 본다: 그 양식의 머리글("약품명", "투약일수", "복용법")이 일반 양식 머리글로도 보여서 일반 쪽이 먼저면 시간대를 놓친다
        List<OcrResult.Item> items = slotItemsFromTables(doc.tables());
        if (items.isEmpty()) {
            items = itemsFromTables(doc.tables());
        }
        if (items.isEmpty()) {
            items = slotItemsFromWords(doc.words());
        }
        if (items.isEmpty()) {
            items = itemsFromRows(rowTexts);
        }
        return new OcrResult(findHospital(allTexts), findPharmacy(allTexts), findDate(allTexts, today), dedupe(items));
    }

    // ------------------------------------------------------------------ 병원, 약국, 날짜

    static String findHospital(List<String> texts) {
        for (String text : texts) {
            Matcher m = HOSPITAL.matcher(text);
            while (m.find()) {
                String name = stripLabel(m.group(1), "의료기관명칭", "의료기관명", "요양기관명", "의료기관", "기관명", "명칭");
                if (name.length() >= 3 && !name.equals("의료기관") && !name.equals("요양기관")) {
                    return name;
                }
            }
        }
        return null;
    }

    static String findPharmacy(List<String> texts) {
        String found = null;
        for (String text : texts) {
            Matcher m = PHARMACY.matcher(text);
            while (m.find()) {
                String name = stripLabel(m.group(1), "조제약국명칭", "조제약국", "약국명칭", "약국명", "명칭");
                // "약국 제출용", "조제약국" 같은 양식 글자는 약국 이름이 아니다. 약국 이름은 보통 아래쪽에 있어서 마지막 것을 쓴다
                if (name.length() >= 3 && !name.contains("제출") && !name.contains("조제") && !name.equals("약국용")) {
                    found = name;
                }
            }
        }
        return found;
    }

    static LocalDate findDate(List<String> texts, LocalDate today) {
        LocalDate fallback = null;
        for (String text : texts) {
            boolean labeled = text.contains("교부") || text.contains("발행") || text.contains("처방일");
            Matcher m = DATE.matcher(text);
            while (m.find()) {
                LocalDate date = toDate(m.group(1), m.group(2), m.group(3));
                // 생년월일 같은 다른 날짜를 걸러내려고 범위를 둔다: 이름표("교부", "발행", "처방일")가 붙은 날짜는 5년 전부터,
                // 이름표가 없는 날짜는 1년 전부터 내일까지만 처방일로 본다 (시험용 처방전은 날짜가 오래된 경우가 많다)
                LocalDate oldest = labeled ? today.minusYears(5) : today.minusDays(365);
                if (date == null || date.isBefore(oldest) || date.isAfter(today.plusDays(1))) {
                    continue;
                }
                if (labeled) {
                    return date;
                }
                if (fallback == null) {
                    fallback = date;
                }
            }
        }
        return fallback;
    }

    private static LocalDate toDate(String y, String m, String d) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static String stripLabel(String name, String... labels) {
        String result = name;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String label : labels) {
                if (result.startsWith(label) && result.length() > label.length()) {
                    result = result.substring(label.length());
                    changed = true;
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 1) 엔진이 인식한 표

    static List<OcrResult.Item> itemsFromTables(List<OcrDocument.Table> tables) {
        List<OcrResult.Item> items = new ArrayList<>();
        for (OcrDocument.Table table : tables) {
            List<List<String>> rows = table.rows();
            if (slotHeaderRow(rows) >= 0) {
                continue;   // 시간대 칸 양식은 slotItemsFromTables가 읽는다 (여기서 읽으면 시간대가 빠진 약이 나온다)
            }
            int header = -1;
            for (int i = 0; i < rows.size(); i++) {
                if (isHeader(String.join("", rows.get(i)))) {
                    header = i;
                    break;
                }
            }
            if (header < 0) {
                continue;
            }
            int nameCol = columnOf(rows.get(header), NAME_KEYS, -1);
            int doseCol = columnOf(rows.get(header), DOSE_KEYS, -1);
            int timesCol = columnOf(rows.get(header), TIMES_KEYS, -1);
            int daysCol = columnOf(rows.get(header), DAYS_KEYS, -1);
            int usageCol = columnOf(rows.get(header), USAGE_KEYS, -1);
            if (nameCol < 0) {
                continue;
            }
            for (int i = header + 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                String joined = tidy(String.join(" ", row));
                if (isFooter(joined)) {
                    break;
                }
                String name = cell(row, nameCol);
                if (isHeaderContinuation(joined) || cleanName(name) == null) {
                    continue;
                }
                OcrResult.Item item = buildItem(name, cell(row, doseCol), cell(row, timesCol), cell(row, daysCol), cell(row, usageCol));
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** 머리글에서 키워드가 들어 있는 첫 칸의 열 번호 (없으면 fallback) */
    private static int columnOf(List<String> header, List<String> keys, int fallback) {
        for (int j = 0; j < header.size(); j++) {
            String text = header.get(j).replaceAll("\\s+", "");
            for (String key : keys) {
                if (text.contains(key)) {
                    // "1일 투여횟수"가 "1회" 키워드로 투약량 열이 되지 않게: 투약량 열은 횟수·일수를 가리키는 글자가 없어야 한다
                    if (keys == DOSE_KEYS && (text.contains("횟수") || text.contains("일수"))) {
                        continue;
                    }
                    return j;
                }
            }
        }
        return fallback;
    }

    private static String cell(List<String> row, int column) {
        return column >= 0 && column < row.size() ? row.get(column) : "";
    }


    // ------------------------------------------------------------------ 시간대 칸 양식 (약품명 | 용량 | 투약일수 | 아침 | 점심 | 저녁)

    private static final List<String> SLOT_NAMES = List.of("MORNING", "LUNCH", "DINNER", "BEDTIME");
    private static final List<String> SLOT_DOSE_KEYS = List.of("용량", "투약량", "1회");
    private static final List<String> SLOT_FOOTER_KEYS = List.of("※", "복약지도", "복약", "위와같이", "주의사항");
    private static final Pattern ROW_NUMBER = Pattern.compile("^\\d{1,2}$");

    /** "아침"이면 MORNING처럼 시간대 이름. 시간대 글자가 아니면 null (글자가 정확히 그것일 때만) */
    private static String slotOf(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", "");
        switch (t) {
            case "아침":
                return "MORNING";
            case "점심":
                return "LUNCH";
            case "저녁":
                return "DINNER";
            case "취침":
            case "취침전":
            case "자기전":
                return "BEDTIME";
            default:
                return null;
        }
    }

    /** 표의 머리글에서 시간대 글자가 둘 이상 있는 행 번호 (앞쪽 4행 안). 없으면 -1 */
    private static int slotHeaderRow(List<List<String>> rows) {
        for (int i = 0; i < Math.min(rows.size(), 4); i++) {
            int slots = 0;
            for (String cell : rows.get(i)) {
                if (slotOf(cell) != null) {
                    slots++;
                }
            }
            if (slots >= 2) {
                return i;
            }
        }
        return -1;
    }

    /** 엔진이 표로 인식한 시간대 칸 양식. 머리글 행 위쪽(합쳐진 칸 포함)에서 약품명·용량·투약일수 열을 찾는다 */
    static List<OcrResult.Item> slotItemsFromTables(List<OcrDocument.Table> tables) {
        List<OcrResult.Item> items = new ArrayList<>();
        for (OcrDocument.Table table : tables) {
            List<List<String>> rows = table.rows();
            int slotRow = slotHeaderRow(rows);
            if (slotRow < 0) {
                continue;
            }
            Map<Integer, String> slotColumns = new TreeMap<>();
            List<String> headerRow = rows.get(slotRow);
            for (int j = 0; j < headerRow.size(); j++) {
                String slot = slotOf(headerRow.get(j));
                if (slot != null) {
                    slotColumns.put(j, slot);
                }
            }
            int nameCol = headerColumn(rows, slotRow, NAME_KEYS);
            int doseCol = headerColumn(rows, slotRow, SLOT_DOSE_KEYS);
            int daysCol = headerColumn(rows, slotRow, DAYS_KEYS);
            if (nameCol < 0) {
                continue;
            }
            for (int i = slotRow + 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (containsAny(String.join("", row).replaceAll("\\s+", ""), SLOT_FOOTER_KEYS)) {
                    break;
                }
                Map<String, String> slotCells = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> e : slotColumns.entrySet()) {
                    slotCells.put(e.getValue(), cell(row, e.getKey()));
                }
                OcrResult.Item item = slotItem(cell(row, nameCol), cell(row, doseCol), cell(row, daysCol), slotCells);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** 머리글 행(마지막 머리글 행 포함) 위쪽에서 키워드가 들어 있는 첫 칸의 열 번호. 없으면 -1 */
    private static int headerColumn(List<List<String>> rows, int lastHeaderRow, List<String> keys) {
        for (int i = 0; i <= lastHeaderRow && i < rows.size(); i++) {
            List<String> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (containsAny(row.get(j).replaceAll("\\s+", ""), keys)) {
                    return j;
                }
            }
        }
        return -1;
    }

    /**
     * 글자 위치로 읽는 시간대 칸 양식 (엔진의 표 인식을 쓰지 않을 때).
     *  - 열: 머리글의 "약품명", "용량", "투약일수", "아침", "점심", "저녁"의 가로 위치. 각 글자는 가장 가까운 열에 넣는다.
     *  - 행: 맨 왼쪽 열(No.)의 번호(1, 2, 3 ...)의 세로 위치. 약 이름이 두 줄이어도(예: 영문 병기) 번호에 가장 가까운 글자가 같은 행이다.
     *  - 표 아래의 안내("※ 복약 지도")부터는 읽지 않는다.
     */
    static List<OcrResult.Item> slotItemsFromWords(List<OcrDocument.Word> words) {
        if (words == null || words.size() < 8) {
            return List.of();
        }
        double h = WordRows.medianHeight(words);

        // 1) 시간대 머리글: 같은 높이에 시간대 글자가 둘 이상
        List<OcrDocument.Word> slotWords = new ArrayList<>();
        for (OcrDocument.Word w : words) {
            if (slotOf(w.text()) != null) {
                slotWords.add(w);
            }
        }
        Map<String, OcrDocument.Word> header = null;
        double headerY = 0;
        slotWords.sort((a, b) -> Double.compare(a.centerY(), b.centerY()));
        for (OcrDocument.Word anchor : slotWords) {
            Map<String, OcrDocument.Word> found = new LinkedHashMap<>();
            for (OcrDocument.Word s : slotWords) {
                if (Math.abs(s.centerY() - anchor.centerY()) <= 0.7 * h) {
                    found.putIfAbsent(slotOf(s.text()), s);
                }
            }
            if (found.size() >= 2) {
                header = found;
                headerY = anchor.centerY();
                break;
            }
        }
        if (header == null) {
            return List.of();
        }
        double headerBottom = header.values().stream().mapToDouble(OcrDocument.Word::bottom).max().orElse(headerY);

        // 2) 다른 열의 머리글: 시간대 글자 행 위아래(합쳐진 머리글 칸은 두 줄 사이에 있다)
        double bandTop = headerY - 7 * h;
        double bandBottom = headerY + 0.7 * h;
        OcrDocument.Word nameW = headerWord(words, bandTop, bandBottom, NAME_KEYS);
        if (nameW == null) {
            return List.of();
        }
        OcrDocument.Word doseW = headerWord(words, bandTop, bandBottom, SLOT_DOSE_KEYS);
        OcrDocument.Word daysW = headerWord(words, bandTop, bandBottom, DAYS_KEYS);
        OcrDocument.Word noW = headerWord(words, bandTop, bandBottom, List.of("no", "번호", "순번"));

        Map<String, Double> columnX = new LinkedHashMap<>();
        columnX.put("name", center(nameW));
        if (doseW != null) {
            columnX.put("dose", center(doseW));
        }
        if (daysW != null) {
            columnX.put("days", center(daysW));
        }
        for (Map.Entry<String, OcrDocument.Word> e : header.entrySet()) {
            columnX.put(e.getKey(), center(e.getValue()));
        }
        List<Map.Entry<String, Double>> columns = new ArrayList<>(columnX.entrySet());
        columns.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));

        // 3) 읽을 범위: 시간대 머리글 아래부터 표 아래 안내 위까지
        double footerTop = Double.MAX_VALUE;
        for (OcrDocument.Word w : words) {
            if (w.centerY() > headerY + h && containsAny(w.text().replaceAll("\\s+", ""), SLOT_FOOTER_KEYS)) {
                footerTop = Math.min(footerTop, w.top());
            }
        }
        List<OcrDocument.Word> data = new ArrayList<>();
        for (OcrDocument.Word w : words) {
            if (w.centerY() > headerBottom && w.top() < footerTop - 1) {
                data.add(w);
            }
        }

        // 4) 행의 기준: 맨 왼쪽(No.) 열의 번호
        double nameX = center(nameW);
        List<OcrDocument.Word> anchors = new ArrayList<>();
        for (OcrDocument.Word w : data) {
            if (ROW_NUMBER.matcher(w.text()).matches() && center(w) < nameX) {
                anchors.add(w);
            }
        }
        if (noW != null) {
            double limit = 0.6 * (nameX - center(noW));
            anchors.removeIf(a -> Math.abs(center(a) - center(noW)) > Math.max(limit, 1.5 * h));
        } else if (!anchors.isEmpty()) {
            double leftmost = anchors.stream().mapToDouble(PrescriptionTextParser::center).min().orElse(0);
            anchors.removeIf(a -> center(a) - leftmost > 1.5 * h);
        }
        if (anchors.isEmpty()) {
            return List.of();
        }
        anchors.sort((a, b) -> Double.compare(a.centerY(), b.centerY()));
        double spacing = 6 * h;
        for (int i = 1; i < anchors.size(); i++) {
            spacing = Math.min(spacing, anchors.get(i).centerY() - anchors.get(i - 1).centerY());
        }
        double reach = spacing / 2 + 1;

        // 5) 각 글자를 가장 가까운 행(번호)과 열에 넣는다
        List<Map<String, List<OcrDocument.Word>>> grid = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            grid.add(new LinkedHashMap<>());
        }
        for (OcrDocument.Word w : data) {
            if (anchors.contains(w)) {
                continue;
            }
            int row = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < anchors.size(); i++) {
                double distance = Math.abs(w.centerY() - anchors.get(i).centerY());
                if (distance < best) {
                    best = distance;
                    row = i;
                }
            }
            if (row < 0 || best > reach) {
                continue;
            }
            grid.get(row).computeIfAbsent(columnAt(columns, center(w)), k -> new ArrayList<>()).add(w);
        }

        List<OcrResult.Item> items = new ArrayList<>();
        for (Map<String, List<OcrDocument.Word>> row : grid) {
            Map<String, String> slotCells = new LinkedHashMap<>();
            for (String slot : header.keySet()) {
                slotCells.put(slot, cellText(row.get(slot)));
            }
            OcrResult.Item item = slotItem(cellText(row.get("name")), cellText(row.get("dose")), cellText(row.get("days")), slotCells);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static double center(OcrDocument.Word w) {
        return (w.left() + w.right()) / 2.0;
    }

    /** 가로 위치가 가장 가까운 열 (열 사이의 중간선을 경계로 한다) */
    private static String columnAt(List<Map.Entry<String, Double>> columns, double x) {
        for (int i = 0; i < columns.size() - 1; i++) {
            double boundary = (columns.get(i).getValue() + columns.get(i + 1).getValue()) / 2.0;
            if (x < boundary) {
                return columns.get(i).getKey();
            }
        }
        return columns.get(columns.size() - 1).getKey();
    }

    private static OcrDocument.Word headerWord(List<OcrDocument.Word> words, double top, double bottom, List<String> keys) {
        OcrDocument.Word best = null;
        for (OcrDocument.Word w : words) {
            if (w.centerY() < top || w.centerY() > bottom) {
                continue;
            }
            String compact = w.text().replaceAll("\\s+", "").toLowerCase(Locale.ROOT).replaceAll("[.:]", "");
            if (containsAny(compact, keys.stream().map(k -> k.toLowerCase(Locale.ROOT)).toList()) && (best == null || w.left() < best.left())) {
                best = w;
            }
        }
        return best;
    }

    /** 칸 안의 글자를 위에서 아래, 왼쪽에서 오른쪽 순서로 공백으로 잇는다 */
    private static String cellText(List<OcrDocument.Word> words) {
        if (words == null) {
            return "";
        }
        List<OcrDocument.Word> sorted = new ArrayList<>(words);
        double tolerance = Math.max(1.0, 0.55 * WordRows.medianHeight(sorted));
        sorted.sort((a, b) -> Math.abs(a.centerY() - b.centerY()) <= tolerance ? Double.compare(a.left(), b.left())
                : Double.compare(a.centerY(), b.centerY()));
        StringBuilder text = new StringBuilder();
        for (OcrDocument.Word w : sorted) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(w.text());
        }
        return text.toString();
    }

    /**
     * 시간대 칸 양식의 약 한 줄. 숫자가 들어 있는 칸("1정", "2", "0.5정")이 그 약을 먹는 시간대이고 "-"나 빈칸은 먹지 않는다.
     * 하루 횟수는 먹는 시간대의 수이고, 1회 투약량은 용량 열이 있으면 그 값, 없으면 먹는 첫 시간대의 값이다
     * (시간대마다 양이 다른 처방은 하나의 값으로 담을 수 없어서 용량 열이나 첫 값을 쓰니 사용자가 확인해야 한다).
     */
    private static OcrResult.Item slotItem(String rawName, String doseText, String daysText, Map<String, String> slotCells) {
        String name = cleanName(rawName);
        if (name == null) {
            return null;
        }
        List<String> marked = new ArrayList<>();
        BigDecimal firstSlotDose = null;
        for (String slot : SLOT_NAMES) {
            String text = slotCells.get(slot);
            if (text != null && text.matches(".*[0-9½].*")) {
                marked.add(slot);
                if (firstSlotDose == null) {
                    firstSlotDose = parseDose(text);
                }
            }
        }
        BigDecimal dose = parseDose(doseText);
        if (dose == null) {
            dose = firstSlotDose;
        }
        Short times = marked.isEmpty() ? null : Short.valueOf((short) marked.size());
        return new OcrResult.Item(name, dose, times, intIn(daysText, 1, 365), false, null, null, marked);
    }

    // ------------------------------------------------------------------ 2), 3) 글자 위치로 묶은 행 / 줄

    static List<OcrResult.Item> itemsFromRows(List<String> rows) {
        int start = headerEnd(rows);
        List<OcrResult.Item> items = new ArrayList<>();
        // 머리글을 못 찾으면 모든 행을 훑되, 숫자 세 개까지 모두 읽힌 행만 약으로 본다(엉뚱한 글자를 약으로 만들지 않으려고)
        boolean strict = start < 0;
        for (int i = Math.max(start, 0); i < rows.size(); i++) {
            String row = rows.get(i);
            if (!strict && isFooter(row)) {
                break;
            }
            Matcher m = ROW.matcher(row);
            if (m.matches()) {
                OcrResult.Item item = buildItem(m.group("name"), m.group("dose"), m.group("times"), m.group("days"), m.group("rest"));
                if (item != null && (!strict || DRUG_LIKE.matcher(item.drugName()).matches())) {
                    items.add(item);
                }
            } else if (!items.isEmpty() && !strict && isUsageContinuation(row)) {
                // 용법이 다음 행으로 내려간 경우: 앞 약의 용법에 합쳐서 다시 읽는다
                OcrResult.Item last = items.remove(items.size() - 1);
                items.add(withUsage(last, row));
            }
        }
        return items;
    }

    /** 머리글이 끝나는 행 번호 + 1 (머리글이 한 행, 두 행, 세 행에 나뉘어 있어도 찾는다). 못 찾으면 -1 */
    private static int headerEnd(List<String> rows) {
        for (int i = 0; i < rows.size(); i++) {
            StringBuilder combined = new StringBuilder();
            for (int span = 1; span <= 3 && i + span <= rows.size(); span++) {
                combined.append(rows.get(i + span - 1));
                if (isHeader(combined.toString())) {
                    return i + span;
                }
            }
        }
        return -1;
    }

    private static boolean isHeader(String text) {
        String compact = text.replaceAll("\\s+", "");
        if (!containsAny(compact, NAME_KEYS)) {
            return false;
        }
        int score = 0;
        for (List<String> group : List.of(DOSE_KEYS, TIMES_KEYS, DAYS_KEYS, USAGE_KEYS)) {
            if (containsAny(compact, group)) {
                score++;
            }
        }
        return score >= 2;
    }

    private static boolean isHeaderContinuation(String text) {
        String compact = text.replaceAll("\\s+", "");
        return containsAny(compact, DOSE_KEYS) && containsAny(compact, TIMES_KEYS) && !compact.matches(".*\\d.*");
    }

    private static boolean isFooter(String text) {
        String compact = text.replaceAll("\\s+", "");
        return containsAny(compact, FOOTER_KEYS);
    }

    private static boolean isUsageContinuation(String row) {
        String compact = row.replaceAll("\\s+", "");
        return (compact.contains("식후") || compact.contains("식전") || compact.contains("필요시") || compact.contains("식사"))
                && !ROW.matcher(row).matches() && compact.length() <= 30;
    }

    private static boolean containsAny(String text, List<String> keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 약 한 줄 만들기

    private static OcrResult.Item buildItem(String rawName, String doseText, String timesText, String daysText, String usageText) {
        String name = cleanName(rawName);
        if (name == null) {
            return null;
        }
        Usage usage = parseUsage(usageText);
        BigDecimal dose = parseDose(doseText);
        Short times = usage.prn() ? null : smallInt(timesText, 1, 4);   // DB 규칙: 필요 시 복용 약은 하루 횟수가 없다
        Integer days = intIn(daysText, 1, 365);
        return new OcrResult.Item(name, dose, times, days, usage.prn(), usage.meal(), usage.offset());
    }

    private static OcrResult.Item withUsage(OcrResult.Item item, String extraUsage) {
        Usage usage = parseUsage(extraUsage);
        boolean prn = item.prn() || usage.prn();
        return new OcrResult.Item(item.drugName(), item.dosePerIntake(), prn ? null : item.timesPerDay(), item.totalDays(), prn,
                item.mealRelation() != null ? item.mealRelation() : usage.meal(),
                item.mealOffsetMin() != null ? item.mealOffsetMin() : usage.offset());
    }

    /** 약 이름을 다듬는다 (보험코드, 표 선, 연속 공백 제거). 약 이름으로 볼 수 없으면 null */
    static String cleanName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = tidy(raw.replaceAll("[|│┃]", " "));
        name = LATIN_NOTE.matcher(name).replaceAll("");   // "(Amlodipine)" 같은 영문 성분명 병기는 약물 DB 검색에 방해가 된다
        name = tidy(name);
        name = LEADING_CODE.matcher(name).replaceFirst("");
        name = name.replaceAll("^[\\-·•*]+", "").trim();
        if (name.length() > 60) {
            name = name.substring(0, 60).trim();
        }
        long letters = name.chars().filter(c -> Character.isLetter(c)).count();
        if (letters < 2 || NAME_NOISE.matcher(name).replaceAll("").isEmpty()) {
            return null;
        }
        String compact = name.replaceAll("\\s+", "");
        if (isHeaderWord(compact)) {
            return null;
        }
        return name;
    }

    private static boolean isHeaderWord(String compact) {
        for (String key : List.of("의약품의명칭", "의약품명칭", "투약량", "투여횟수", "투약일수", "용법", "처방의약품")) {
            if (compact.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** "1", "0.5", "1/2", "½", "1정" 같은 1회 투약량. 0 이하이거나 20을 넘으면 null */
    static BigDecimal parseDose(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.replace("½", "1/2").replaceAll("\\s+", "");
        Matcher fraction = Pattern.compile("(\\d{1,2})/(\\d{1,2})").matcher(t);
        BigDecimal value = null;
        if (fraction.find()) {
            int b = Integer.parseInt(fraction.group(2));
            if (b != 0) {
                value = new BigDecimal(Integer.parseInt(fraction.group(1))).divide(new BigDecimal(b), 2, RoundingMode.HALF_UP);
            }
        } else {
            Matcher number = Pattern.compile("(\\d{1,2}(?:\\.\\d{1,2})?)").matcher(t);
            if (number.find()) {
                value = new BigDecimal(number.group(1)).setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(20)) > 0) {
            return null;
        }
        return value;
    }

    private static Short smallInt(String text, int min, int max) {
        Integer value = intIn(text, min, max);
        return value == null ? null : value.shortValue();
    }

    private static Integer intIn(String text, int min, int max) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,4})").matcher(text);
        if (!m.find()) {
            return null;
        }
        int value = Integer.parseInt(m.group(1));
        return value >= min && value <= max ? value : null;
    }

    /** 용법 글자에서 필요 시 복용, 식사 관계, 식전·식후 몇 분 */
    record Usage(boolean prn, PatientMedication.MealRelation meal, Short offset) {
    }

    static Usage parseUsage(String text) {
        if (text == null || text.isBlank()) {
            return new Usage(false, null, null);
        }
        String t = tidy(text);
        boolean prn = PRN.matcher(t).find();
        String compact = t.replaceAll("\\s+", "");
        PatientMedication.MealRelation meal = null;
        if (compact.contains("식사와함께") || compact.contains("식사중") || compact.contains("식중")) {
            meal = PatientMedication.MealRelation.WITH_MEAL;
        } else {
            int after = compact.indexOf("식후");
            int before = compact.indexOf("식전");
            if (after >= 0 && (before < 0 || after < before)) {
                meal = PatientMedication.MealRelation.AFTER_MEAL;
            } else if (before >= 0) {
                meal = PatientMedication.MealRelation.BEFORE_MEAL;
            }
        }
        Short offset = null;
        if (meal == PatientMedication.MealRelation.AFTER_MEAL || meal == PatientMedication.MealRelation.BEFORE_MEAL) {
            Matcher m = MEAL_OFFSET.matcher(t);
            if (m.find()) {
                int minutes = Integer.parseInt(m.group(2));
                offset = minutes <= 240 ? (short) minutes : null;
            }
        }
        return new Usage(prn, meal, offset);
    }

    // ------------------------------------------------------------------ 도우미

    private static List<OcrResult.Item> dedupe(List<OcrResult.Item> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<OcrResult.Item> result = new ArrayList<>();
        for (OcrResult.Item item : items) {
            String key = item.drugName().replaceAll("\\s+", "").toLowerCase(Locale.ROOT) + "|" + item.dosePerIntake()
                    + "|" + item.timesPerDay() + "|" + item.totalDays();
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    /** 표 선(|)과 연속 공백을 정리한다 */
    private static String tidy(String text) {
        return text == null ? "" : text.replaceAll("[|│┃]", " ").replaceAll("\\s+", " ").trim();
    }
}