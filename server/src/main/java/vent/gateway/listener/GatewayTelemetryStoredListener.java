package vent.gateway.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vent.gateway.service.GatewayConnectionService;
import vent.telemetry.service.TelemetryStoredEvent;

/**
 * 측정값 저장 완료 이벤트를 게이트웨이 수신으로 반영하는 처리기.
 *
 * <p>측정값이 저장됐다는 것은 그 현장의 게이트웨이가 살아 있다는 뜻이다. 측정값 저장이 커밋된 뒤 게이트웨이의
 * 마지막 수신 시각을 갱신하고 연결됨으로 바꾼다. 측정값 기능은 게이트웨이 기능을 몰라도 된다(ADR-0013).
 * 실패해도 측정값은 이미 저장됐으므로 로그만 남기고, 다음 측정값(30초 뒤)에서 다시 반영된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@Component
public class GatewayTelemetryStoredListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayTelemetryStoredListener.class);

    private final GatewayConnectionService connectionService;

    public GatewayTelemetryStoredListener(GatewayConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetryStored(TelemetryStoredEvent event) {
        try {
            connectionService.markReceived(event.gatewayId(), event.receivedAt());
        } catch (RuntimeException e) {
            log.error("게이트웨이 수신 반영 실패, 다음 측정값에서 다시 반영: {} ({})", event.gatewayId(), event.messageId(), e);
        }
    }
}
