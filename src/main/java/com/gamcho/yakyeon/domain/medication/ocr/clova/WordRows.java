package com.gamcho.yakyeon.domain.medication.ocr.clova;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 글자 조각들을 위치(세로 좌표)로 묶어 "한 줄"로 다시 만든다 (스프링·DB 없는 순수 로직).
 * 처방전은 표라서 약 이름, 투약량, 횟수, 일수가 서로 다른 칸이지만 같은 높이에 있다. 엔진이 알려 준 줄바꿈과 상관없이
 * 같은 높이의 글자를 왼쪽부터 이어 붙이면 표의 한 행이 된다.
 */
public final class WordRows {

    /** 같은 높이로 묶인 글자들과 이어 붙인 글자 (칸 사이는 공백 하나) */
    public record Row(String text, List<OcrDocument.Word> words) {
    }

    private WordRows() {
    }

    public static List<Row> group(List<OcrDocument.Word> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        double tolerance = Math.max(1.0, 0.55 * medianHeight(words));
        List<OcrDocument.Word> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(OcrDocument.Word::centerY).thenComparingDouble(OcrDocument.Word::left));

        List<List<OcrDocument.Word>> clusters = new ArrayList<>();
        List<OcrDocument.Word> current = new ArrayList<>();
        double sum = 0;
        for (OcrDocument.Word word : sorted) {
            if (!current.isEmpty() && Math.abs(word.centerY() - sum / current.size()) > tolerance) {
                clusters.add(current);
                current = new ArrayList<>();
                sum = 0;
            }
            current.add(word);
            sum += word.centerY();
        }
        clusters.add(current);

        List<Row> rows = new ArrayList<>();
        for (List<OcrDocument.Word> cluster : clusters) {
            cluster.sort(Comparator.comparingDouble(OcrDocument.Word::left));
            StringBuilder text = new StringBuilder();
            for (OcrDocument.Word w : cluster) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(w.text());
            }
            rows.add(new Row(text.toString(), List.copyOf(cluster)));
        }
        return rows;
    }

    static double medianHeight(List<OcrDocument.Word> words) {
        double[] heights = words.stream().mapToDouble(OcrDocument.Word::height).filter(h -> h > 0).sorted().toArray();
        return heights.length == 0 ? 20.0 : heights[heights.length / 2];
    }
}