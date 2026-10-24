package vent.site.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import vent.gateway.domain.Gateway;
import vent.gateway.domain.type.ConnectionState;

/**
 * 게이트웨이 응답.
 *
 * <p>현장 응답 안에 들어가는 게이트웨이 id와 연결 상태. 화면은 마지막 수신 시각으로 "마지막 수신 N분 전"을 보여 주고,
 * 연결됨이 아니면 장비를 확인 불가로 표시한다(#6).
 *
 * @param id              게이트웨이 ID
 * @param connectionState 연결 상태
 * @param lastReceivedAt  서버가 마지막으로 받은 시각 (#6). 한 번도 받지 않았으면 null
 * @param disconnectedAt  끊긴 시각 (#6). 일시 끊김·오프라인일 때만
 * @param firmwareVersion 게이트웨이가 보고한 버전 (#6)
 * @param capabilities    게이트웨이가 보고한 지원 기능 (#6)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public record GatewayResponse(
        String id,
        ConnectionState connectionState,
        OffsetDateTime lastReceivedAt,
        OffsetDateTime disconnectedAt,
        String firmwareVersion,
        List<String> capabilities) {

    /** 게이트웨이가 아직 등록되지 않은 현장이면 null을 돌려준다. */
    public static GatewayResponse from(Gateway gateway) {
        return gateway == null ? null : new GatewayResponse(
                gateway.getId(),
                gateway.getConnectionState(),
                gateway.getLastReceivedAt(),
                gateway.getDisconnectedAt(),
                gateway.getFirmwareVersion(),
                gateway.getCapabilities());
    }
}
