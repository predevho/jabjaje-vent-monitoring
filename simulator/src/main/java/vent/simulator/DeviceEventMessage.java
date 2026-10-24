package vent.simulator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * VEMC가 보내는 장비 이벤트 메시지 (스펙 4.2, #7).
 *
 * <p>서버의 {@code vent.hood.dto.request.DeviceEventMessage}와 같은 형식이다. 형식을 바꿀 때는 필드 추가만 한다(ADR-0004).
 *
 * @param schemaVersion 메시지 형식 버전
 * @param messageId     중복 확인용 ID
 * @param deviceId      VEMC 장비 ID
 * @param type          {@code HOOD_ON} 또는 {@code HOOD_OFF}
 * @param occurredAt    일어난 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public record DeviceEventMessage(int schemaVersion, UUID messageId, String deviceId, String type, OffsetDateTime occurredAt) {

    static DeviceEventMessage of(String deviceId, String type, OffsetDateTime occurredAt) {
        return new DeviceEventMessage(TelemetryMessage.SCHEMA_VERSION, UUID.randomUUID(), deviceId, type, occurredAt);
    }
}
