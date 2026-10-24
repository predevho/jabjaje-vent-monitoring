package vent.servicerequest.service;

/**
 * A/S 신청 접수 이벤트 (#10). 저장 트랜잭션이 커밋된 뒤 백그라운드에서 AI 소견을 만든다.
 *
 * @param serviceRequestId 접수된 A/S 신청 ID
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record ServiceRequestReceivedEvent(Long serviceRequestId) {
}
