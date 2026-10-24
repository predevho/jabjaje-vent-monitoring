package vent.servicerequest.client;

/**
 * AI 소견 호출부 (스펙 8.6, #10).
 *
 * <p>실제 구현(Claude, #12)과 가짜 구현({@link FakeAssessmentClient})을 바꿔 끼울 수 있게 인터페이스로 둔다.
 * API 키가 없거나 네트워크가 안 되면 가짜 구현으로 끝까지 시연할 수 있다. 실패하면 예외를 던지고, 부르는 쪽이
 * 사람 확인 필요로 넘긴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public interface AssessmentClient {

    AssessmentReply assess(AssessmentInput input);
}
