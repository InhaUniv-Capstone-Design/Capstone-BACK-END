package com.gamcho.yakyeon.domain.medication.ocr.clova;

import java.util.List;

/**
 * 외부 OCR이 이미지에서 읽어낸 글자 (엔진의 응답 형식과 무관한 중립적인 모양).
 *  - words: 인식된 글자 조각과 위치(좌표). 같은 줄에 있는 글자를 위치로 다시 묶을 때 쓴다.
 *  - lines: 엔진이 알려 준 줄 단위 글자 (읽는 순서).
 *  - tables: 엔진이 표로 인식한 영역 (표 인식을 켠 경우에만 있다). 칸 글자를 행·열 순서로 담는다.
 */
public record OcrDocument(List<Word> words, List<String> lines, List<Table> tables) {

    public static OcrDocument empty() {
        return new OcrDocument(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return words.isEmpty() && lines.isEmpty() && tables.isEmpty();
    }

    /** 글자 조각 하나. 좌표는 이미지 기준(왼쪽 위가 0,0) */
    public record Word(String text, double left, double top, double right, double bottom, double confidence) {

        public double centerY() {
            return (top + bottom) / 2.0;
        }

        public double height() {
            return Math.max(0, bottom - top);
        }
    }

    /** 표. rows[행][열] = 칸 글자 (비어 있으면 빈 문자열) */
    public record Table(List<List<String>> rows) {
    }
}