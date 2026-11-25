package com.gamcho.yakyeon.domain.dose.job;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업(복용 일정 점검 등)을 켠다 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}