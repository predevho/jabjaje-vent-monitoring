package vent.command.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 원격 명령이 재시도 뒤에도 응답이 없어 시간 초과가 됐다는 이벤트 (#9).
 *
 * <p>상태를 바꾼 트랜잭션 안에서 발행되고, 받는 쪽(COMMAND_TIMEOUT 이상)도 같은 트랜잭션에서 처리한다(ADR-0014와 같은 방식).
 *
 * @param commandId 명령 ID
 * @param deviceId  대상 장비 ID
 * @param at        시간 초과로 바꾼 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
public record RemoteCommandTimedOutEvent(UUID commandId, String deviceId, OffsetDateTime at) {
}
