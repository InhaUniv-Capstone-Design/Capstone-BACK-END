package com.gamcho.yakyeon.domain.device.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import com.gamcho.yakyeon.domain.device.logic.DeviceSecrets;
import com.gamcho.yakyeon.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 약통(기기)이 보낸 요청의 인증. 기기는 사람의 로그인 토큰이 없어서 요청마다 헤더로
 * 시리얼번호(X-Device-Serial)와 비밀값(X-Device-Secret)을 보낸다. 서버는 비밀값의 해시를 저장된 값과 비교한다.
 *
 * 실패하면 이유와 상관없이 똑같은 오류를 준다 - 헤더 없음, 등록되지 않은 기기, 해제된 기기, 틀린 비밀값을
 * 응답으로 구분할 수 없어야 시리얼번호가 등록돼 있는지 알아내려는 시도를 막을 수 있다.
 * 등록되지 않은 기기에도 같은 해시 계산을 해서 응답 시간으로도 구분되지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class DeviceAuthService {

    private static final int MAX_SERIAL_LENGTH = 40;
    private static final int MAX_SECRET_LENGTH = 128;

    private final DeviceRepository deviceRepository;

    /** @return 인증된 기기 (호출한 쪽의 트랜잭션 안에서 바로 갱신할 수 있는 영속 상태) */
    public Device authenticate(String serialNo, String secret) {
        if (serialNo == null || secret == null || serialNo.isBlank() || secret.isBlank()
                || serialNo.length() > MAX_SERIAL_LENGTH || secret.length() > MAX_SECRET_LENGTH) {
            throw new BusinessException(ErrorCode.DEVICE_AUTH_FAILED);
        }
        Optional<Device> device = deviceRepository.findBySerialNoAndStatus(serialNo, DeviceStatus.REGISTERED);
        String storedHash = device.map(Device::getSecretHash).orElse(DeviceSecrets.DUMMY_HASH);
        boolean matches = DeviceSecrets.matches(secret, storedHash);
        if (device.isEmpty() || !matches) {
            throw new BusinessException(ErrorCode.DEVICE_AUTH_FAILED);
        }
        return device.get();
    }
}