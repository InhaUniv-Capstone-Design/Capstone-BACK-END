package com.gamcho.yakyeon.domain.medication.repository;

import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
}