package vent.hood.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 장비가 MQTT로 보내는 이벤트 메시지 ({@code jabjaje/{현장ID}/{장비ID}/event}, 스펙 4.2).
 *
 * <p>모르는 필드는 무시한다(ADR-0004). 장비 ID는 토픽 값을 믿고, 이 값은 토픽과 같은지 확인하는 데만 쓴다.
 *
 * @param schemaVersion 메시지 형식 버전
 * @param messageId     중복 확인용 ID
 * @param deviceId      보낸 장비 ID
 * @param type          {@code HOOD_ON} 또는 {@code HOOD_OFF}
 * @param occurredAt    장비에서 일어난 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceEventMessage(Integer schemaVersion, UUID messageId, String deviceId, String type,
                                 OffsetDateTime occurredAt) {

    /** 저장에 필요한 값이 모두 있는지. */
    public boolean hasAllRequiredFields() {
        return Stream.of(messageId, deviceId, type, occurredAt).allMatch(value -> value != null);
    }
}
