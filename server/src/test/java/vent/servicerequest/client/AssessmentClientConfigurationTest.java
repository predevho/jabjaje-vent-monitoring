package vent.servicerequest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * AI 소견 구현 고르기 단위 테스트.
 *
 * <p>테스트 지점: 설정과 환경변수로 구현을 고르는 메서드. 기준은 #12 결정이다: auto는 키가 있으면 Claude, claude·fake는 키와
 * 상관없이 그대로, 모르는 값이면 시작하지 않는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
class AssessmentClientConfigurationTest {

    @Test
    @DisplayName("auto는 키가 있으면 Claude, 없거나 비어 있으면 가짜 AI")
    void autoDependsOnKey() {
        assertThat(AssessmentClientConfiguration.useClaude(env("auto", "sk-test"))).isTrue();
        assertThat(AssessmentClientConfiguration.useClaude(env("auto", null))).isFalse();
        assertThat(AssessmentClientConfiguration.useClaude(env("auto", "  "))).isFalse();
        assertThat(AssessmentClientConfiguration.useClaude(env(null, null))).isFalse();
    }

    @Test
    @DisplayName("fake는 키가 있어도 가짜 AI, claude는 키가 없어도 Claude(빈 생성에서 시작 오류)")
    void explicitModes() {
        assertThat(AssessmentClientConfiguration.useClaude(env("fake", "sk-test"))).isFalse();
        assertThat(AssessmentClientConfiguration.useClaude(env("claude", null))).isTrue();
    }

    @Test
    @DisplayName("모르는 값이면 시작하지 않는다")
    void unknownModeFails() {
        assertThatThrownBy(() -> AssessmentClientConfiguration.useClaude(env("openai", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockEnvironment env(String mode, String key) {
        MockEnvironment environment = new MockEnvironment();
        if (mode != null) {
            environment.setProperty("vent.assessment.client", mode);
        }
        if (key != null) {
            environment.setProperty(AssessmentClientConfiguration.API_KEY_ENV, key);
        }
        return environment;
    }
}
