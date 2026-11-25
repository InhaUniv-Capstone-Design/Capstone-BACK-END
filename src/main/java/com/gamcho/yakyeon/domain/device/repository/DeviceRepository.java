package com.gamcho.yakyeon.domain.device.repository;

import com.gamcho.yakyeon.domain.device.entity.Device;
import com.gamcho.yakyeon.domain.device.entity.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findBySerialNoAndStatus(String serialNo, DeviceStatus status);

    Optional<Device> findByPatientIdAndStatus(Long patientId, DeviceStatus status);

    boolean existsBySerialNoAndStatus(String serialNo, DeviceStatus status);

    boolean existsByPatientIdAndStatus(Long patientId, DeviceStatus status);
}