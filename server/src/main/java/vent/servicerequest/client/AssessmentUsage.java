package vent.servicerequest.client;

/**
 * AI 호출 한 번의 토큰 사용량 (#12). 평가 세트가 비용을 계산할 때 쓴다. 가짜 AI는 모두 0이다.
 *
 * @param inputTokens      입력 토큰 (캐시에서 읽은 것 제외)
 * @param outputTokens     출력 토큰 (생각 포함)
 * @param cacheReadTokens  캐시에서 읽은 입력 토큰
 * @param cacheWriteTokens 캐시에 쓴 입력 토큰
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public record AssessmentUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

    /** 사용량이 없을 때 (가짜 AI) */
    public static final AssessmentUsage NONE = new AssessmentUsage(0, 0, 0, 0);
}
