package vent.telemetry.service;

import java.util.UUID;

/**
 * 측정값 저장 완료 이벤트.
 *
 * <p>측정값이 새로 저장되면 {@link TelemetryIngestService}가 낸다. 이상 기능이 이 이벤트를 받아 규칙을 평가한다.
 * 측정값 기능은 누가 이 이벤트를 받는지 모른다 (ADR-0013).
 *
 * @param deviceId  측정값을 보낸 장비 ID
 * @param messageId 저장된 측정값의 messageId
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record TelemetryStoredEvent(String deviceId, UUID messageId) {
}
