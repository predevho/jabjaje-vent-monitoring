package vent.servicerequest.domain;

/**
 * AI 소견 검증에서 걸린 항목 하나 (스펙 8.5, #10).
 *
 * <p>{@code ai_assessment.validation_errors}에 JSON으로 저장하는 값 타입이다.
 *
 * @param code    종류: INVALID_JSON, MISSING_FIELD, INVALID_FIELD, UNKNOWN_EVIDENCE, UNKNOWN_PART, FORBIDDEN_PHRASE,
 *                CLIENT_ERROR(AI를 부를 수 없음), REFUSAL(안전 거절, #12), TRUNCATED(응답 잘림, #12),
 *                SUMMARY_ERROR(상황 요약을 만들 수 없음)
 * @param message 사람이 읽을 설명
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record AssessmentValidationError(String code, String message) {
}
