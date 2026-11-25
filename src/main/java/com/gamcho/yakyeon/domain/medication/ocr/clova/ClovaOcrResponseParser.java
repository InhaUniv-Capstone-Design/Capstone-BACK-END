package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.domain.medication.ocr.OcrUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLOVA OCR General API의 응답(JSON)을 OcrDocument로 바꾼다 (스프링 없는 순수 로직).
 * 응답 형식은 네이버 클라우드 문서(General OCR, V2) 기준이다.
 *
 *  images[0].inferResult : SUCCESS(성공) / FAILURE(글자를 인식하지 못함) / ERROR(엔진 처리 오류)
 *  images[0].fields[]    : inferText, inferConfidence, lineBreak(그 줄의 마지막 글자인가), boundingPoly.vertices[x,y]
 *  images[0].tables[]    : (표 인식을 켠 경우) cells[]: rowIndex, columnIndex, cellTextLines[].cellWords[].inferText
 *
 * 처리 규칙
 *  - FAILURE는 오류가 아니라 "읽은 글자가 없음"이다(빈 결과). ERROR와 알 수 없는 형식은 OcrUnavailableException이다.
 *  - lineBreak가 하나라도 있으면 그 표시대로 줄을 만들고, 없으면(V1) 글자 위치로 줄을 묶는다.
 *  - 오류 메시지에 응답 내용을 담지 않는다(처방전 글자가 로그에 남지 않게).
 */
public final class ClovaOcrResponseParser {

    private static final int MAX_FIELDS = 30_000;
    private static final int MAX_CELLS = 30_000;

    private ClovaOcrResponseParser() {
    }

    public static OcrDocument parse(String json) {
        Map<String, Object> root;
        try {
            root = MiniJson.asMap(MiniJson.parse(json));
        } catch (IllegalArgumentException e) {
            throw new OcrUnavailableException("unexpected-response");
        }
        List<Object> images = root == null ? null : MiniJson.asList(root.get("images"));
        Map<String, Object> image = images == null || images.isEmpty() ? null : MiniJson.asMap(images.get(0));
        if (image == null) {
            throw new OcrUnavailableException("unexpected-response");
        }

        String result = MiniJson.asString(image.get("inferResult"));
        if ("ERROR".equals(result)) {
            throw new OcrUnavailableException("infer-error");
        }
        if ("FAILURE".equals(result)) {
            return OcrDocument.empty();
        }
        List<Object> fields = MiniJson.asList(image.get("fields"));
        List<Object> tables = MiniJson.asList(image.get("tables"));
        if (!"SUCCESS".equals(result) && fields == null && tables == null) {
            throw new OcrUnavailableException("unexpected-response");
        }

        List<OcrDocument.Word> words = new ArrayList<>();
        List<Boolean> lineEnds = new ArrayList<>();
        boolean anyLineBreakInfo = false;
        if (fields != null) {
            if (fields.size() > MAX_FIELDS) {
                throw new OcrUnavailableException("response-too-large");
            }
            for (Object o : fields) {
                Map<String, Object> field = MiniJson.asMap(o);
                if (field == null) {
                    continue;
                }
                String text = MiniJson.asString(field.get("inferText"));
                if (text == null || text.isBlank()) {
                    continue;
                }
                double[] box = boundingBox(field);
                words.add(new OcrDocument.Word(text.trim(), box[0], box[1], box[2], box[3],
                        MiniJson.asDouble(field.get("inferConfidence"), 1.0)));
                lineEnds.add(MiniJson.asBoolean(field.get("lineBreak"), false));
                anyLineBreakInfo |= field.containsKey("lineBreak");
            }
        }

        return new OcrDocument(List.copyOf(words), anyLineBreakInfo ? linesFromBreaks(words, lineEnds) : linesFromGeometry(words),
                tablesOf(tables));
    }

    /** vertices 네 점의 가장 왼쪽·위·오른쪽·아래. 좌표가 없으면 0 */
    private static double[] boundingBox(Map<String, Object> field) {
        Map<String, Object> poly = MiniJson.asMap(field.get("boundingPoly"));
        List<Object> vertices = poly == null ? null : MiniJson.asList(poly.get("vertices"));
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        boolean any = false;
        if (vertices != null) {
            for (Object v : vertices) {
                Map<String, Object> point = MiniJson.asMap(v);
                if (point == null) {
                    continue;
                }
                double x = MiniJson.asDouble(point.get("x"), Double.NaN);
                double y = MiniJson.asDouble(point.get("y"), Double.NaN);
                if (Double.isNaN(x) || Double.isNaN(y)) {
                    continue;
                }
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                any = true;
            }
        }
        return any ? new double[]{left, top, right, bottom} : new double[]{0, 0, 0, 0};
    }

    private static List<String> linesFromBreaks(List<OcrDocument.Word> words, List<Boolean> lineEnds) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(words.get(i).text());
            if (lineEnds.get(i)) {
                lines.add(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    private static List<String> linesFromGeometry(List<OcrDocument.Word> words) {
        return WordRows.group(words).stream().map(WordRows.Row::text).toList();
    }

    private static List<OcrDocument.Table> tablesOf(List<Object> tables) {
        if (tables == null) {
            return List.of();
        }
        List<OcrDocument.Table> result = new ArrayList<>();
        int cellCount = 0;
        for (Object t : tables) {
            Map<String, Object> table = MiniJson.asMap(t);
            List<Object> cells = table == null ? null : MiniJson.asList(table.get("cells"));
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            cellCount += cells.size();
            if (cellCount > MAX_CELLS) {
                throw new OcrUnavailableException("response-too-large");
            }
            int rowCount = 0;
            int columnCount = 0;
            for (Object c : cells) {
                Map<String, Object> cell = MiniJson.asMap(c);
                if (cell == null) {
                    continue;
                }
                rowCount = Math.max(rowCount, (int) MiniJson.asDouble(cell.get("rowIndex"), -1) + 1);
                columnCount = Math.max(columnCount, (int) MiniJson.asDouble(cell.get("columnIndex"), -1) + 1);
            }
            if (rowCount <= 0 || columnCount <= 0 || rowCount > 500 || columnCount > 50) {
                continue;
            }
            String[][] grid = new String[rowCount][columnCount];
            for (Object c : cells) {
                Map<String, Object> cell = MiniJson.asMap(c);
                if (cell == null) {
                    continue;
                }
                int row = (int) MiniJson.asDouble(cell.get("rowIndex"), -1);
                int column = (int) MiniJson.asDouble(cell.get("columnIndex"), -1);
                if (row < 0 || column < 0) {
                    continue;
                }
                grid[row][column] = cellText(cell);
            }
            List<List<String>> rows = new ArrayList<>();
            for (String[] r : grid) {
                List<String> row = new ArrayList<>();
                for (String value : r) {
                    row.add(value == null ? "" : value);
                }
                rows.add(List.copyOf(row));
            }
            result.add(new OcrDocument.Table(List.copyOf(rows)));
        }
        return List.copyOf(result);
    }

    /** 칸 안의 모든 줄·단어를 공백으로 이어 붙인다 */
    private static String cellText(Map<String, Object> cell) {
        List<Object> lines = MiniJson.asList(cell.get("cellTextLines"));
        if (lines == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Object l : lines) {
            Map<String, Object> line = MiniJson.asMap(l);
            List<Object> cellWords = line == null ? null : MiniJson.asList(line.get("cellWords"));
            if (cellWords == null) {
                continue;
            }
            for (Object w : cellWords) {
                Map<String, Object> word = MiniJson.asMap(w);
                String t = word == null ? null : MiniJson.asString(word.get("inferText"));
                if (t != null && !t.isBlank()) {
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(t.trim());
                }
            }
        }
        return text.toString();
    }
}