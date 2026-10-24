package vent.gateway.service;

import java.time.OffsetDateTime;
import vent.gateway.domain.type.ConnectionState;

/**
 * 게이트웨이 연결 상태가 바뀌었다는 이벤트.
 *
 * <p>상태를 바꾼 트랜잭션 안에서 발행되고, 받는 쪽(오프라인 이상)도 같은 트랜잭션에서 처리한다(ADR-0014).
 *
 * @param gatewayId      게이트웨이 ID
 * @param from           바뀌기 전 상태
 * @param to             바뀐 상태
 * @param at             바뀐 시각 (검사 시각 또는 수신 시각)
 * @param disconnectedAt 끊긴 시각(마지막 수신 시각). 연결됨으로 돌아온 경우에는 바뀌기 전 값
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
public record GatewayConnectionChangedEvent(
        String gatewayId,
        ConnectionState from,
        ConnectionState to,
        OffsetDateTime at,
        OffsetDateTime disconnectedAt) {
}
