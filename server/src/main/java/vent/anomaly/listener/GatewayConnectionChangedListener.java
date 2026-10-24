package vent.anomaly.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vent.anomaly.service.GatewayOfflineAnomalyService;
import vent.gateway.service.GatewayConnectionChangedEvent;

/**
 * 게이트웨이 연결 상태 변경 이벤트 수신 처리기.
 *
 * <p>측정값 이벤트(#4)와 달리 커밋 후가 아니라 상태를 바꾼 트랜잭션 안에서 바로 처리한다({@code @EventListener}).
 * 오프라인 상태와 오프라인 이상은 항상 함께 저장되거나 함께 취소되어야 하기 때문이다(ADR-0014).
 * 여기서 예외가 나면 상태 변경도 롤백되고, 다음 검사나 다음 수신 때 다시 시도된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@Component
public class GatewayConnectionChangedListener {

    private final GatewayOfflineAnomalyService offlineAnomalyService;

    public GatewayConnectionChangedListener(GatewayOfflineAnomalyService offlineAnomalyService) {
        this.offlineAnomalyService = offlineAnomalyService;
    }

    @EventListener
    public void onConnectionChanged(GatewayConnectionChangedEvent event) {
        offlineAnomalyService.onConnectionChanged(event);
    }
}
