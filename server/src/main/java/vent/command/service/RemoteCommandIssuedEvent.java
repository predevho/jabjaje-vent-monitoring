package vent.command.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import vent.command.domain.BoostParams;

/**
 * 원격 명령을 보냄으로 저장했다는 이벤트. 저장이 커밋된 뒤 게이트웨이로 발행한다 (#8).
 *
 * @param commandId 명령 ID
 * @param siteId    장비의 현장 ID (토픽의 첫 단계)
 * @param deviceId  대상 장비 ID
 * @param params    부스트 파라미터
 * @param issuedAt  요청 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record RemoteCommandIssuedEvent(UUID commandId, Long siteId, String deviceId, BoostParams params,
                                       OffsetDateTime issuedAt) {
}
