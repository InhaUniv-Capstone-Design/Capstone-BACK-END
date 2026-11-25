package com.gamcho.yakyeon.domain.medication.ocr;

/**
 * 업로드된 파일의 실제 형식을 파일 앞부분의 시그니처(매직 바이트)로 판별한다.
 * 클라이언트가 보내는 Content-Type 헤더나 파일 확장자는 마음대로 속일 수 있어서 믿지 않는다.
 * (시그니처가 맞아도 안전한 이미지라는 보장은 아니다 - 형식이 다른 파일을 걸러내는 1차 방어일 뿐이다.)
 */
public final class ImageSignature {

    public enum Type { JPEG, PNG }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageSignature() {
    }

    /** JPEG 또는 PNG이면 그 형식, 아니면 null */
    public static Type detect(byte[] data) {
        if (startsWith(data, JPEG)) {
            return Type.JPEG;
        }
        if (startsWith(data, PNG)) {
            return Type.PNG;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}