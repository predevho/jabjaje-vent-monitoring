package vent.servicerequest.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 실제 Claude AI 소견 설정 ({@code vent.assessment.claude.*}, #12, ADR-0016).
 *
 * <p>API 키는 여기 두지 않는다. 환경변수 {@code ANTHROPIC_API_KEY}로만 받는다.
 *
 * @param model          1차 시도 모델
 * @param retryModel     검증 실패 뒤 재시도 모델. 1차와 다르면 단계적 승격(싼 모델 먼저, 실패하면 강한 모델)이 된다
 * @param effort         생각의 양: low, medium, high, xhigh, max
 * @param maxTokens      응답 최대 토큰 (생각 포함)
 * @param timeout        호출 한 번의 시간 제한
 * @param maxRetries     일시 오류(요청 한도 초과, 과부하, 연결 끊김, 시간 초과) 때 SDK가 다시 시도하는 수
 * @param serverFallback 안전 분류기가 거절하면 Anthropic 서버가 다른 모델로 다시 실행하게 할지 ({@code fallbacks: "default"}).
 *                       이 기능이 있는 모델(Opus 5.5·Opus 5·Sonnet 5.5·Fable 5.1)에만 보낸다
 * @param baseUrl        API 주소. 비우면 기본 주소. 테스트에서 가짜 서버로 바꿀 때 쓴다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
@ConfigurationProperties(prefix = "vent.assessment.claude")
public record ClaudeAssessmentProperties(@DefaultValue("claude-opus-5-5") String model,
                                         @DefaultValue("claude-opus-5-5") String retryModel,
                                         @DefaultValue("medium") String effort,
                                         @DefaultValue("16000") long maxTokens,
                                         @DefaultValue("60s") Duration timeout,
                                         @DefaultValue("1") int maxRetries,
                                         @DefaultValue("true") boolean serverFallback,
                                         String baseUrl) {

    /** SDK가 재시도 전에 기다릴 수 있는 시간 (서버가 알려 주는 retry-after의 상한으로 어림) */
    static final Duration RETRY_WAIT_ALLOWANCE = Duration.ofSeconds(60);

    /** 재시도와 재시도 전 대기까지 포함해 호출 한 번이 걸릴 수 있는 최대 시간. */
    public Duration worstCaseCallTime() {
        return timeout.multipliedBy(maxRetries + 1L).plus(RETRY_WAIT_ALLOWANCE.multipliedBy(maxRetries));
    }
}
