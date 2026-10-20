package com.gamcho.yakyeon.domain.interaction.repository;

import com.gamcho.yakyeon.domain.interaction.entity.InteractionCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InteractionCheckRepository extends JpaRepository<InteractionCheck, Long> {

    /** 복약자의 가장 최근 성공 검사. 같은 시각이면 번호가 큰(나중에 만든) 것이 최근이다 */
    Optional<InteractionCheck> findFirstByPatientIdAndStatusOrderByCheckedAtDescCheckIdDesc(
            Long patientId, InteractionCheck.Status status);
}