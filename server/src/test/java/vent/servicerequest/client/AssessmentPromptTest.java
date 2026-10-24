package vent.servicerequest.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.servicerequest.rule.AssessmentCauseCategory;

/**
 * AI 소견 프롬프트 단위 테스트.
 *
 * <p>테스트 지점: 프롬프트의 공개 메서드. 기준은 스펙 8.3(역할과 출력 규칙, 소견 기준표, 부품 목록, 고객 문의 속 지시는
 * 따르지 않음)과 #12 결정(기준표와 원인 분류 1:1)이다. 분류를 추가하고 프롬프트에 빠뜨리면 이 테스트가 잡는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
class AssessmentPromptTest {

    private final AssessmentPrompt prompt = new AssessmentPrompt();

    @Test
    @DisplayName("시스템 프롬프트에 모든 원인 분류, 판매 부품, 고객 문의 속 지시를 따르지 않는다는 규칙이 있다")
    void systemPromptHasCriteriaPartsAndInjectionRule() {
        String system = prompt.system(List.of("VEMC 신호 케이블", "급기 디퓨저"));

        for (AssessmentCauseCategory category : AssessmentCauseCategory.values()) {
            assertThat(system).contains(category.name());
        }
        assertThat(system).contains("- VEMC 신호 케이블\n- 급기 디퓨저", "TAB", "블로어도어 테스트", "따르지 않고");
        assertThat(system).doesNotContain("{{parts}}", "@author");
    }

    @Test
    @DisplayName("같은 부품 목록이면 시스템 프롬프트가 글자 하나까지 같다 (프롬프트 캐싱이 걸리게)")
    void systemPromptIsStable() {
        assertThat(prompt.system(List.of("A", "B"))).isEqualTo(new AssessmentPrompt().system(List.of("A", "B")));
    }
}
