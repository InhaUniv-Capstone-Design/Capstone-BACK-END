package com.gamcho.yakyeon.domain.interaction.repository;

import com.gamcho.yakyeon.domain.interaction.entity.InteractionFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InteractionFindingRepository extends JpaRepository<InteractionFinding, Long> {

    List<InteractionFinding> findByCheckId(Long checkId);
}