package vent.command.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 장비가 원격 명령에 응답(적용 또는 거절)했다는 이벤트 (#9).
 *
 * <p>장비가 응답한다는 것은 명령이 다시 오가고 있다는 뜻이라, 받는 쪽은 그 장비의 COMMAND_TIMEOUT 이상을 해제한다
 * (스펙 6.1 "다음 명령 정상", 늦은 응답 포함). 응답을 반영한 트랜잭션 안에서 처리한다.
 *
 * @param commandId 명령 ID
 * @param deviceId  응답한 장비 ID
 * @param lateAck   시간 초과 뒤에 온 응답인지
 * @param at        응답을 받은 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
public record RemoteCommandAnsweredEvent(UUID commandId, String deviceId, boolean lateAck, OffsetDateTime at) {
}
