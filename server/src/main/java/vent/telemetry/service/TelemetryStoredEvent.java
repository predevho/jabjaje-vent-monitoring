package vent.telemetry.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 측정값 저장 완료 이벤트.
 *
 * <p>측정값이 새로 저장되면 {@link TelemetryIngestService}가 낸다. 이상 기능은 규칙을 평가하고(#4),
 * 게이트웨이 기능은 마지막 수신 시각을 갱신한다(#6).
 * 측정값 기능은 누가 이 이벤트를 받는지 모른다 (ADR-0013).
 *
 * @param deviceId   측정값을 보낸 장비 ID
 * @param gatewayId  그 장비를 연결하는 게이트웨이 ID (#6)
 * @param messageId  저장된 측정값의 messageId
 * @param receivedAt 서버가 받은 시각 (#6)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record TelemetryStoredEvent(String deviceId, String gatewayId, UUID messageId, OffsetDateTime receivedAt) {
}
