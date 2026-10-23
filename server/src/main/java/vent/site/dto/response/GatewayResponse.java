package vent.site.dto.response;

import vent.site.domain.Gateway;
import vent.site.domain.type.ConnectionState;

/**
 * 게이트웨이 응답.
 *
 * <p>현장 응답 안에 들어가는 게이트웨이 id와 연결 상태.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public record GatewayResponse(String id, ConnectionState connectionState) {

    /** 게이트웨이가 아직 등록되지 않은 현장이면 null을 돌려준다. */
    public static GatewayResponse from(Gateway gateway) {
        return gateway == null ? null : new GatewayResponse(gateway.getId(), gateway.getConnectionState());
    }
}
