package vent.command.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import vent.command.domain.BoostParams;

/**
 * 원격 명령을 게이트웨이로 보내라는 이벤트. 저장(또는 재시도 기록)이 커밋된 뒤 발행한다 (#8, #9).
 *
 * @param commandId 명령 ID
 * @param siteId    장비의 현장 ID (토픽의 첫 단계)
 * @param deviceId  대상 장비 ID
 * @param params    부스트 파라미터
 * @param issuedAt  요청 시각. 장비는 몰려온 명령 중 이 값이 가장 최신인 것만 적용한다 (스펙 7.2)
 * @param expiresAt 만료 시각 (#9). 재시도해도 같다
 * @param retry     재시도인지 (#9)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record RemoteCommandIssuedEvent(UUID commandId, Long siteId, String deviceId, BoostParams params,
                                       OffsetDateTime issuedAt, OffsetDateTime expiresAt, boolean retry) {
}
