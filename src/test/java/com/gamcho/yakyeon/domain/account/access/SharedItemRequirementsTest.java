package com.gamcho.yakyeon.domain.account.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharedItemRequirementsTest {

    private static final List<String> ALL = SharedItemRules.ALL;

    @Test
    @DisplayName("접근 가드가 쓰는 자원 종류 12개가 모두 정해져 있다 (새 자원을 만들면 여기도 올려야 한다)")
    void everyKnownResourceTypeIsCovered() {
        Set<String> expected = Set.of("patient", "prescription", "medication", "patient_medication", "prescription_ocr",
                "dose", "schedule_setting", "interaction", "device", "pillbox", "compartment", "share_summary");
        assertThat(SharedItemRequirements.knownResourceTypes().size()).isEqualTo(expected.size());
        assertThat(SharedItemRequirements.knownResourceTypes().containsAll(expected)).isTrue();
    }

    @Test
    @DisplayName("명세 4-1 표대로: 복약자 정보는 이름·연락처, 약·처방은 약물 정보")
    void personalAndMedication() {
        assertThat(SharedItemRequirements.requiredFor("patient").get(0)).isEqualTo("PERSONAL_INFO");
        for (String type : new String[]{"prescription", "medication", "patient_medication"}) {
            assertThat(SharedItemRequirements.requiredFor(type).get(0)).as(type).isEqualTo("MED_INFO");
            assertThat(SharedItemRequirements.requiredFor(type).size()).as(type).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("복용 일정·기록·홈 요약·복용 시각 설정은 복약 기록, 병용금기는 병용금기 정보, 약통은 약통 이벤트")
    void doseInteractionDevice() {
        assertThat(SharedItemRequirements.requiredFor("dose").get(0)).isEqualTo("DOSE_RECORD");
        assertThat(SharedItemRequirements.requiredFor("schedule_setting").get(0)).isEqualTo("DOSE_RECORD");
        assertThat(SharedItemRequirements.requiredFor("interaction").get(0)).isEqualTo("DUR_INFO");
        for (String type : new String[]{"device", "pillbox", "compartment"}) {
            assertThat(SharedItemRequirements.requiredFor(type).get(0)).as(type).isEqualTo("DEVICE_EVENT");
        }
    }

    @Test
    @DisplayName("처방전 사진 인식은 열람 항목이 필요 없다 (서버에 사진을 보관·열람하는 기능이 없다)")
    void ocrNeedsNothing() {
        assertThat(SharedItemRequirements.requiredFor("prescription_ocr").isEmpty()).isTrue();
        assertThat(SharedItemRequirements.allows("prescription_ocr", List.of())).isTrue();
    }

    @Test
    @DisplayName("복약 이력 공유 요약은 이름·생년월일, 약, 병용금기 세 항목이 모두 필요하다")
    void shareSummaryNeedsThree() {
        List<String> required = SharedItemRequirements.requiredFor("share_summary");
        assertThat(required.size()).isEqualTo(3);
        assertThat(required.containsAll(List.of("PERSONAL_INFO", "MED_INFO", "DUR_INFO"))).isTrue();
        assertThat(SharedItemRequirements.allows("share_summary", List.of("PERSONAL_INFO", "MED_INFO", "DUR_INFO"))).isTrue();
        assertThat(SharedItemRequirements.allows("share_summary", List.of("PERSONAL_INFO", "MED_INFO"))).as("병용금기 빠짐").isFalse();
        assertThat(SharedItemRequirements.allows("share_summary", List.of("MED_INFO", "DUR_INFO"))).as("이름·연락처 빠짐").isFalse();
        assertThat(SharedItemRequirements.allows("share_summary", List.of("PERSONAL_INFO", "DUR_INFO"))).as("약물 정보 빠짐").isFalse();
    }

    @Test
    @DisplayName("필요한 항목이 허용 목록에 모두 있어야 통과한다")
    void allowsOnlyWhenAllRequiredAreShared() {
        assertThat(SharedItemRequirements.allows("dose", List.of("PERSONAL_INFO", "MED_INFO", "PHOTO"))).as("필수 3개만").isFalse();
        assertThat(SharedItemRequirements.allows("dose", List.of("PERSONAL_INFO", "MED_INFO", "PHOTO", "DOSE_RECORD"))).isTrue();
        assertThat(SharedItemRequirements.allows("device", List.of("DOSE_RECORD", "DUR_INFO"))).isFalse();
        assertThat(SharedItemRequirements.allows("device", List.of("DEVICE_EVENT"))).isTrue();
        assertThat(SharedItemRequirements.allows("interaction", List.of())).as("허용 항목 없음(옛 연동)").isFalse();
        assertThat(SharedItemRequirements.allows("patient", ALL)).isTrue();
    }

    @Test
    @DisplayName("모르는 자원 종류는 보호자에게 열어 주지 않는다 (항목이 전부 허용돼 있어도)")
    void unknownResourceIsDenied() {
        assertThat(SharedItemRequirements.requiredFor("something_new") == null).isTrue();
        assertThat(SharedItemRequirements.allows("something_new", ALL)).isFalse();
        assertThat(SharedItemRequirements.allows("", ALL)).isFalse();
        assertThat(SharedItemRequirements.allows(null, ALL)).isFalse();
    }

    @Test
    @DisplayName("필요한 항목은 모두 6개 열람 항목 안에 있고, 사진(PHOTO)은 어떤 자원에도 필요하지 않다")
    void requiredItemsAreValid() {
        for (String type : SharedItemRequirements.knownResourceTypes()) {
            for (String item : SharedItemRequirements.requiredFor(type)) {
                assertThat(ALL.contains(item)).as(type + " → " + item).isTrue();
                assertThat(item.equals("PHOTO")).as(type + "에 PHOTO 요구").isFalse();
            }
        }
    }
}