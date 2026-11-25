package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.drug.entity.Drug;
import com.gamcho.yakyeon.domain.drug.repository.DrugRepository;
import com.gamcho.yakyeon.domain.medication.dto.MedicationItem;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 약 한 건의 입력 규칙 검사와 약물 확정. 처방 등록(여러 건)과 약 수정(한 건)이 같은 규칙을 쓰도록 모아 둔 곳.
 *
 * DB 제약과 트리거가 막는 조합을 500이 아니라 400으로, 어느 약의 어떤 문제인지 알려주며 막는다.
 * 규칙은 DB 제약(필요 시 복용의 횟수·시기, 식사 관계)과, DB가 막지 않지만 우리가 정한 규칙(횟수와 시기 개수 일치)이다.
 */
@Component
@RequiredArgsConstructor
public class MedicationInputValidator {

    private final DrugRepository drugRepository;

    /** 검사를 통과한 약 한 건 (저장할 값을 정리해 둔 것) */
    public record ValidatedMedication(MedicationItem item, Drug drug, String inputName, boolean prn,
                                      Short timesPerDay, List<TimingSlot> slots) {
    }

    /** 요청에 담긴 drug_id를 한 번에 조회한다. DB에 없는 drug_id가 하나라도 있으면 404 */
    public Map<Long, Drug> loadDrugs(Collection<MedicationItem> items) {
        Set<Long> ids = items.stream().map(MedicationItem::getDrugId)
                .filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Drug> found = drugRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Drug::getDrugId, Function.identity()));
        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw new BusinessException(ErrorCode.DRUG_NOT_FOUND,
                        "존재하지 않는 약물입니다. (drug_id: " + id + ")");
            }
        }
        return found;
    }

    /**
     * @param where 오류 메시지 앞에 붙일 위치 설명. 여러 건을 받는 처방 등록은 "2번째 약: ", 한 건을 받는 수정은 ""
     */
    public ValidatedMedication validate(String where, MedicationItem item, Map<Long, Drug> drugs) {
        boolean prn = Boolean.TRUE.equals(item.getPrn());
        List<TimingSlot> slots = item.getTimingSlots() == null ? List.of() : item.getTimingSlots();

        // 약 확정과 이름
        Drug drug = item.getDrugId() == null ? null : drugs.get(item.getDrugId());
        String inputName = blankToNull(item.getInputName());
        if (inputName == null && drug != null) {
            inputName = drug.getProductName();
        }
        if (inputName == null) {
            throw invalid(where + "약 이름(input_name)이 필요합니다. (약물을 선택하지 않은 경우 직접 입력한 이름이 필요합니다.)");
        }

        // 복용 시기 중복
        if (new HashSet<>(slots).size() != slots.size()) {
            throw invalid(where + "복용 시기(timing_slots)가 중복되었습니다.");
        }

        Short timesPerDay;
        if (prn) {
            // 필요 시 복용: 복용 횟수·시기를 가질 수 없다 (DB CHECK ck_med_prn_times + 트리거)
            if (item.getTimesPerDay() != null || !slots.isEmpty()) {
                throw invalid(where + "필요 시 복용 약은 복용 횟수와 복용 시기를 가질 수 없습니다.");
            }
            timesPerDay = null;
        } else {
            if (slots.isEmpty()) {
                throw invalid(where + "복용 시기(timing_slots)를 1개 이상 선택해주세요. (필요 시 복용이면 is_prn을 true로 보내주세요.)");
            }
            if (item.getTimesPerDay() != null && item.getTimesPerDay() != slots.size()) {
                throw invalid(where + "하루 복용 횟수(" + item.getTimesPerDay()
                        + "회)와 복용 시기 개수(" + slots.size() + "개)가 다릅니다.");
            }
            timesPerDay = (short) slots.size();
        }

        // 식사 전후 시간은 식사 관계와 함께만 (DB CHECK ck_med_meal_offset)
        if (item.getMealOffsetMin() != null && item.getMealRelation() == null) {
            throw invalid(where + "식사 전후 시간(meal_offset_min)은 식사 관계(meal_relation)와 함께 보내주세요.");
        }

        return new ValidatedMedication(item, drug, inputName, prn, timesPerDay, slots);
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_MEDICATION, message);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}