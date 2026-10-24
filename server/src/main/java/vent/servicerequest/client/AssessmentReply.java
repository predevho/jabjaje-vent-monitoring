package vent.servicerequest.client;

/**
 * AI가 돌려준 응답 (#10). 검증은 받는 쪽이 한다.
 *
 * @param model   응답한 모델 이름 (가짜면 fake-…)
 * @param rawText 응답 원문 (JSON이어야 하지만 믿지 않는다)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record AssessmentReply(String model, String rawText) {
}
