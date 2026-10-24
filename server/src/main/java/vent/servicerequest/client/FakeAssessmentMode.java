package vent.servicerequest.client;

/**
 * 가짜 AI 응답 방식 (#10). 설정에는 소문자와 하이픈으로 쓴다(예: {@code bad-part}).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public enum FakeAssessmentMode {
    /** 시나리오별 정상 응답 */
    NORMAL,
    /** 판매 목록에 없는 부품 */
    BAD_PART,
    /** 상황 요약에 없는 근거 */
    BAD_EVIDENCE,
    /** 고객 안내문에 무상 교체 약속 */
    FORBIDDEN_PHRASE,
    /** 형식이 깨진 JSON */
    BROKEN_JSON,
    /** 첫 시도만 틀리고 재시도는 정상 */
    FLAKY,
    /** AI를 부를 수 없음 (예외) */
    UNAVAILABLE
}
