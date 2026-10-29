package com.gamcho.yakyeon.domain.notification.logic;

/**
 * 서버가 만들어 내는 알림의 종류와 문구. 푸시 알림은 잠금 화면에 보이고 Google 서버를 거치므로
 * 문구에는 약 이름·병명·복용량 같은 건강정보를 넣지 않고 일반 문구만 쓴다 (자세한 내용은 앱 안에서 본다).
 * channel은 안드로이드 알림 채널 이름이며 DB의 CHECK 제약(INTERACTION, INTAKE, DEVICE, STOCK)과 같아야 한다.
 */
public enum NotificationKind {

    /** 약통에서 복용이 확인됨 (복용 확인 버튼) */
    INTAKE_DETECTED("INTAKE", "복용이 확인됐어요", "약통의 복용 버튼이 눌렸어요."),

    /** 약통이 열렸지만 허용 시간 안에 복용 확인 버튼이 눌리지 않음 */
    DEVICE_OPENED("DEVICE", "약통이 열렸어요", "약통이 열렸지만 복용 확인 버튼은 눌리지 않았어요."),

    /** 예정 시각이 지났는데도 복용이 기록되지 않음 */
    MISSED_DOSE("INTAKE", "복약이 확인되지 않았어요", "예정 시간이 지났지만 복약이 감지되지 않았어요."),

    /** 약통의 센서나 버튼, 연결에 이상이 생김 */
    DEVICE_STATUS("DEVICE", "약통 상태를 확인해주세요", "약통의 센서나 연결에 이상이 감지됐어요."),

    /** 약통의 약봉지가 기준 이하로 남음 */
    LOW_STOCK("STOCK", "약통의 약이 얼마 남지 않았어요", "약통에 남은 약봉지가 적어요. 약을 채워주세요."),

    /** 복용 시간이 다가옴: 예정 10분 전, 복약자 본인에게 (정각에는 약통이 울린다) */
    DOSE_REMINDER("INTAKE", "복용할 시간이 다가와요", "곧 복용할 시간이에요. 약을 챙겨주세요."),

    /**
     * 예정 30분이 지났는데도 복용이 기록되지 않음: 복약자 본인용 문구. 저장되는 알림 종류는 보호자용과 같은 MISSED_DOSE이고
     * (DB의 CHECK 제약에 이 이름이 없다), 받는 사람이 다르므로 같은 예정에 두 건이 만들어져도 중복이 아니다.
     */
    MISSED_DOSE_SELF("MISSED_DOSE", "INTAKE", "복용이 아직 기록되지 않았어요", "예정 시간이 지났어요. 복용하셨다면 앱에서 기록해주세요.");

    /** DB에 저장되는 알림 종류(notification.notif_type). 대부분 상수 이름과 같다 */
    private final String type;
    private final String channel;
    private final String title;
    private final String body;

    NotificationKind(String channel, String title, String body) {
        this.type = name();
        this.channel = channel;
        this.title = title;
        this.body = body;
    }

    NotificationKind(String type, String channel, String title, String body) {
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.body = body;
    }

    public String type() {
        return type;
    }

    public String channel() {
        return channel;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }
}