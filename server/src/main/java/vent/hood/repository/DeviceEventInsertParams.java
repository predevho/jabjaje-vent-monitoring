package vent.hood.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 장비 이벤트 저장 값. 이름 붙은 SQL 파라미터를 구성 요소 이름으로 채운다.
 *
 * @param deviceId   장비 ID (토픽 값)
 * @param messageId  messageId
 * @param type       {@code HOOD_ON} 또는 {@code HOOD_OFF}
 * @param occurredAt 장비에서 일어난 시각
 * @param receivedAt 서버가 받은 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public record DeviceEventInsertParams(String deviceId, UUID messageId, String type, OffsetDateTime occurredAt,
                                      OffsetDateTime receivedAt) {
}
