package com.gamcho.yakyeon.domain.account.access;

import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 보호자가 복약자의 어떤 정보에 접근하려면 복약자가 어떤 열람 항목을 허용해야 하는지 (스프링·DB 없는 순수 규칙).
 * 키는 접근 가드(PatientAccessGuard.check)에 넘기는 자원 종류(resourceType)이고, 값은 필요한 열람 항목이다.
 *
 * 명세(4-1)의 확정 표를 그대로 따른다:
 *  - 복약자 정보(이름·생년월일 등)                          → PERSONAL_INFO
 *  - 약·처방 조회·등록·수정·삭제·종료                        → MED_INFO
 *  - 복용 일정, 복용 기록, 홈 요약, 먹었어요/안 먹었어요, 복용 시각 설정 → DOSE_RECORD
 *  - 병용금기 목록·요약·그래프·상세                          → DUR_INFO
 *  - 약통 조회·등록·칸 배정                                 → DEVICE_EVENT
 *  - 처방전·약봉지 사진                                     → 없음 (서버에 사진을 보관·열람하는 기능이 없어 PHOTO 적용 대상이 없다)
 *  - 복약 이력 공유 요약(이름·생년월일, 약, 병용금기가 한 화면에 나온다) → PERSONAL_INFO + MED_INFO + DUR_INFO
 *
 * 목록에 없는 자원 종류는 보호자에게 허용하지 않는다(새 API를 만들고 여기에 올리는 것을 잊으면 열리는 쪽이 아니라 막히는 쪽으로
 * 어긋나게 하려는 것이다). 복약자 본인에게는 이 규칙을 적용하지 않는다.
 */
public final class SharedItemRequirements {

    private static final Map<String, List<String>> REQUIRED = Map.ofEntries(
            Map.entry("patient", List.of(SharedItemRules.PERSONAL_INFO)),
            Map.entry("prescription", List.of(SharedItemRules.MED_INFO)),
            Map.entry("medication", List.of(SharedItemRules.MED_INFO)),
            Map.entry("patient_medication", List.of(SharedItemRules.MED_INFO)),
            Map.entry("prescription_ocr", List.of()),
            Map.entry("dose", List.of(SharedItemRules.DOSE_RECORD)),
            Map.entry("schedule_setting", List.of(SharedItemRules.DOSE_RECORD)),
            Map.entry("interaction", List.of(SharedItemRules.DUR_INFO)),
            Map.entry("device", List.of(SharedItemRules.DEVICE_EVENT)),
            Map.entry("pillbox", List.of(SharedItemRules.DEVICE_EVENT)),
            Map.entry("compartment", List.of(SharedItemRules.DEVICE_EVENT)),
            Map.entry("share_summary", List.of(SharedItemRules.PERSONAL_INFO, SharedItemRules.MED_INFO,
                    SharedItemRules.DUR_INFO)));

    private SharedItemRequirements() {
    }

    /** 이 자원에 필요한 열람 항목. 필요한 항목이 없으면 빈 목록, 모르는 자원 종류(null 포함)면 null */
    public static List<String> requiredFor(String resourceType) {
        return resourceType == null ? null : REQUIRED.get(resourceType);
    }

    /** 이 규칙이 아는 자원 종류 */
    public static Set<String> knownResourceTypes() {
        return REQUIRED.keySet();
    }

    /** 복약자가 shared 항목들을 허용했을 때 보호자가 이 자원에 접근할 수 있는가. 모르는 자원 종류는 false */
    public static boolean allows(String resourceType, Collection<String> shared) {
        List<String> required = requiredFor(resourceType);
        return required != null && shared.containsAll(required);
    }
}