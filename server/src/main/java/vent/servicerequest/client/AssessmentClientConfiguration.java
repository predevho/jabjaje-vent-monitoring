package vent.servicerequest.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import vent.servicerequest.service.AssessmentProperties;

/**
 * AI 소견 구현 고르기 (#12).
 *
 * <p>{@code vent.assessment.client}로 고른다.
 * <ul>
 *   <li>{@code auto}(기본): 환경변수 {@code ANTHROPIC_API_KEY}가 있으면 Claude, 없거나 비어 있으면 가짜 AI</li>
 *   <li>{@code claude}: 항상 Claude. 키가 없으면 앱이 시작하지 않는다(시연 직전에 키 누락을 바로 알게)</li>
 *   <li>{@code fake}: 항상 가짜 AI. 테스트는 이 값을 강제한다(개발자 키로 실제 비용이 나가지 않게)</li>
 * </ul>
 * 실행 중 Claude가 실패해도 가짜 AI로 바꾸지 않는다. 가짜 소견을 실제 소견으로 착각하게 되기 때문이다. 실패는 사람 확인
 * 필요로 넘긴다(ADR-0005). 시작할 때 어느 쪽을 쓰는지 로그에 남긴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
@Configuration
public class AssessmentClientConfiguration {

    static final String API_KEY_ENV = "ANTHROPIC_API_KEY";
    private static final Logger log = LoggerFactory.getLogger(AssessmentClientConfiguration.class);

    @Bean
    public AssessmentPrompt assessmentPrompt() {
        return new AssessmentPrompt();
    }

    @Bean
    @Conditional(UseClaude.class)
    public AssessmentClient claudeAssessmentClient(Environment environment, ClaudeAssessmentProperties properties,
                                                   AssessmentProperties assessmentProperties, AssessmentPrompt prompt) {
        ClaudeAssessmentClient client = newClaudeClient(environment.getProperty(API_KEY_ENV), properties,
                assessmentProperties, prompt);
        log.info("AI 소견: Claude (1차 {}, 재시도 {}, effort {}, 프롬프트 {})",
                properties.model(), properties.retryModel(), properties.effort(), AssessmentPrompt.VERSION);
        return client;
    }

    /**
     * Claude 호출부를 만든다. 앱과 평가 세트 실행기가 같이 쓴다(같은 설정 검사, 같은 SDK 설정).
     *
     * @throws IllegalStateException 키가 없거나, 호출 최대 시간이 시도 한 번의 제한({@code call-timeout})보다 길 때
     */
    public static ClaudeAssessmentClient newClaudeClient(String apiKey, ClaudeAssessmentProperties properties,
                                                         AssessmentProperties assessmentProperties, AssessmentPrompt prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Claude를 쓰려면 환경변수 " + API_KEY_ENV + "가 필요함");
        }
        if (properties.worstCaseCallTime().compareTo(assessmentProperties.callTimeout()) > 0) {
            throw new IllegalStateException("Claude 호출 최대 시간(timeout × (max-retries + 1) + 재시도 대기 = "
                    + properties.worstCaseCallTime() + ")이 vent.assessment.call-timeout(" + assessmentProperties.callTimeout()
                    + ")보다 김");
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries());
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        AnthropicClient anthropic = builder.build();
        return new ClaudeAssessmentClient(anthropic, properties, prompt);
    }

    @Bean
    @Conditional(UseFake.class)
    public FakeAssessmentClient fakeAssessmentClient(FakeAssessmentProperties properties) {
        log.info("AI 소견: 가짜 AI (응답 방식 {}). 실제 Claude는 {}를 넣고 vent.assessment.client=auto 또는 claude",
                properties.mode(), API_KEY_ENV);
        return new FakeAssessmentClient(properties);
    }

    /** 설정과 환경변수로 Claude를 쓸지 정한다. 모르는 값이면 시작하지 않는다. */
    static boolean useClaude(Environment environment) {
        String mode = environment.getProperty("vent.assessment.client", "auto").strip();
        String apiKey = environment.getProperty(API_KEY_ENV);
        return switch (mode) {
            case "claude" -> true;
            case "fake" -> false;
            case "auto" -> apiKey != null && !apiKey.isBlank();
            default -> throw new IllegalStateException("vent.assessment.client는 auto, claude, fake 중 하나: " + mode);
        };
    }

    /**
     * Claude를 쓸 때.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#12)
     */
    static class UseClaude implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return useClaude(context.getEnvironment());
        }
    }

    /**
     * 가짜 AI를 쓸 때.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#12)
     */
    static class UseFake implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !useClaude(context.getEnvironment());
        }
    }
}
