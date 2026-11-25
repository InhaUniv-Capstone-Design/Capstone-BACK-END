package com.gamcho.yakyeon.domain.drug.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 의약품(제품) 마스터 (drug). 약물명 자동완성과 병용금기 매칭의 기준.
 *
 * 이 데이터는 식약처 공공 데이터를 외부 수집 작업(dur_loader)이 채우므로, 앱은 읽기만 한다.
 * 그래서 @Immutable로 두어 앱 코드가 실수로 수정하지 못하게 한다.
 * 매칭 기준 키는 item_seq(품목기준코드, 9자리 문자열)이며 DB에서 유일하다.
 */
@Entity
@Table(name = "drug")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "drug_id")
    private Long drugId;

    @Column(name = "item_seq", nullable = false, length = 20)
    private String itemSeq;

    /** DB 컬럼이 TEXT라 columnDefinition을 명시한다 (@Lob은 PostgreSQL에서 oid로 잡혀 검증 실패) */
    @Column(name = "product_name", nullable = false, columnDefinition = "TEXT")
    private String productName;

    @Column(name = "manufacturer", columnDefinition = "TEXT")
    private String manufacturer;

    /** 마지막으로 갱신한 DUR 수집 회차 (FK는 DB가 관리, 앱은 값만 읽음) */
    @Column(name = "dur_dataset_id")
    private Long durDatasetId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}