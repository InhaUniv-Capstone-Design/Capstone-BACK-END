package com.gamcho.yakyeon.client.octomo;

/**
 * OCTOMO(문자 인증 업체) 연동. 일반 SMS 인증과 방향이 반대다: 서버가 인증번호를 문자로 보내는 것이 아니라,
 * 사용자가 자기 휴대폰에서 OCTOMO 대표번호로 서버가 만든 인증번호를 문자로 보내고, 서버는 "이 번호에서 이 문구가
 * 최근에 왔는가"를 조회한다. 문자는 사용자의 휴대폰에서 나가므로 그 번호의 주인만 인증할 수 있다.
 */
public interface OctomoClient {

    /**
     * 이 번호가 대표번호로 이 문구를 최근 withinMinutes분 안에 보냈는지 조회한다.
     *
     * @param mobileNum     010으로 시작하는 11자리 숫자
     * @param text          사용자가 보낸 문자 본문 (영문·숫자)
     * @param withinMinutes 1~60
     * @throws OctomoUnavailableException OCTOMO가 응답하지 않거나 오류로 응답했을 때(호출 한도 포함)
     */
    boolean messageExists(String mobileNum, String text, int withinMinutes);

    /** 사용자가 문자를 보낼 대표번호 (숫자만, 예: 16663538) */
    String representativeNumber();
}