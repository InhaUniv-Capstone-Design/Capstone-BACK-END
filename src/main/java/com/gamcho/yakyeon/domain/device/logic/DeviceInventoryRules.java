package com.gamcho.yakyeon.domain.device.logic;

/**
 * 약통 등록 때 출고 약통 목록(device_inventory)과 대조하는 규칙 (스프링·DB 없는 순수 로직).
 *
 * 약통은 만들 때 시리얼번호와 정답 비밀값의 SHA-256 해시가 출고 목록에 미리 들어간다. 사용자가 앱에서 시리얼번호와 비밀값
 * (약통 하단 라벨에 적힌 값)을 입력하면 서버가 이 목록과 대조해서, 목록에 있고 비밀값이 맞을 때만 등록을 진행한다.
 *
 * 목록에 없는 시리얼이든 비밀값이 틀렸든 결과가 똑같아야 한다(응답 문구도, 응답 시간도). 그래야 "이 시리얼은 출고된 약통이다"를
 * 알아내려는 시도를 막을 수 있다. 그래서 목록에 없을 때도 같은 해시 계산과 상수 시간 비교를 한다(DeviceSecrets.DUMMY_HASH).
 */
public final class DeviceInventoryRules {

    private DeviceInventoryRules() {
    }

    /**
     * 이 비밀값으로 등록할 수 있는 약통인가.
     *
     * @param secret              사용자가 입력한 비밀값 원문
     * @param inventorySecretHash 출고 목록에 있는 그 시리얼의 비밀값 해시. 목록에 없으면 null
     */
    public static boolean isRegistrable(String secret, String inventorySecretHash) {
        String expected = inventorySecretHash == null ? DeviceSecrets.DUMMY_HASH : inventorySecretHash;
        boolean matches = DeviceSecrets.matches(secret, expected);
        return inventorySecretHash != null && matches;
    }
}