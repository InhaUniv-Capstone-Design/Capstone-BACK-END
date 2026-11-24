package com.gamcho.yakyeon.domain.notification.push;

/**
 * 화면 갱신용 데이터 푸시 한 건. 알림 창을 띄우지 않고 앱에게 "다시 조회하라"고만 알린다.
 * title, body가 없고 알림 번호도 없다 (notification 테이블에 남기지 않는 푸시다).
 * 앱은 이 값으로 화면을 그리지 않는다. patient_id와 compartment_no는 로그용이다.
 *
 * @param type          푸시 종류 (지금은 IR_TRIGGER 하나)
 * @param patientId     약통의 복약자 번호
 * @param compartmentNo 칸 번호
 */
public record DataPush(String fcmToken, String type, long patientId, int compartmentNo) {

    /** 약통 IR 센서 상태(pouch_present)가 바뀌어 DB에 저장된 뒤 보내는 종류 */
    public static final String IR_TRIGGER = "IR_TRIGGER";
}