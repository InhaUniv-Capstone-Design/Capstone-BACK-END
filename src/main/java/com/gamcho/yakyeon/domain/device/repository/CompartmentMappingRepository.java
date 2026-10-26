package com.gamcho.yakyeon.domain.device.repository;

import com.gamcho.yakyeon.domain.device.entity.CompartmentMapping;
import com.gamcho.yakyeon.domain.device.entity.MappingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompartmentMappingRepository extends JpaRepository<CompartmentMapping, Long> {

    /** 이 약통의 아직 끝나지 않은(충전 전 + 사용 중) 배정 */
    List<CompartmentMapping> findByDeviceIdAndStatusNot(Long deviceId, MappingStatus status);

    boolean existsByDeviceIdAndCompartmentNoAndStatusNot(Long deviceId, Short compartmentNo, MappingStatus status);

    boolean existsByPrescriptionIdAndStatusNot(Long prescriptionId, MappingStatus status);

    /** 충전·추가 충전·해제는 읽고 고쳐서 쓰는 방식이라, 동시에 같은 칸을 바꾸면 한쪽이 덮어쓰지 않게 행을 잠그고 읽는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CompartmentMapping m where m.mappingId = :id")
    Optional<CompartmentMapping> findByIdForUpdate(@Param("id") Long id);

    /** 권한 확인용으로 복약자 번호만 읽는다 (잠그지 않는다) */
    @Query("select m.patientId from CompartmentMapping m where m.mappingId = :id")
    Optional<Long> findPatientId(@Param("id") Long id);
}