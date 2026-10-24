package vent.anomaly.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.anomaly.repository.AnomalyRepository;
import vent.gateway.domain.type.ConnectionState;
import vent.gateway.service.GatewayConnectionChangedEvent;

/**
 * 게이트웨이 오프라인 이상 유스케이스 ({@code GATEWAY_OFFLINE}, 스펙 6.1).
 *
 * <p>게이트웨이가 오프라인이 되면 이상을 열고, 오프라인에서 다시 연결되면 해결한다. 일시 끊김은 이상을 만들지 않는다.
 * 게이트웨이 상태를 바꾼 트랜잭션에 반드시 함께 들어가야 해서 {@code MANDATORY}로 둔다. 상태만 커밋되고 이상이 빠지면
 * 다음 검사는 "이미 오프라인"이라 이상을 다시 만들지 않기 때문이다(ADR-0014).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@Service
public class GatewayOfflineAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(GatewayOfflineAnomalyService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final AnomalyRepository anomalyRepository;

    public GatewayOfflineAnomalyService(AnomalyRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onConnectionChanged(GatewayConnectionChangedEvent event) {
        if (event.to() == ConnectionState.OFFLINE) {
            open(event);
        } else if (event.from() == ConnectionState.OFFLINE) {
            resolve(event);
        }
    }

    private void open(GatewayConnectionChangedEvent event) {
        if (findOpen(event.gatewayId()) != null) {
            return;
        }
        long minutes = Duration.between(event.disconnectedAt(), event.at()).toMinutes();
        String message = "게이트웨이 연결이 끊긴 지 %d분이 넘었습니다 (마지막 수신 %s). 현장 Wi-Fi를 확인해 주세요"
                .formatted(minutes, TIME.format(event.disconnectedAt()));
        anomalyRepository.save(Anomaly.openForGateway(
                event.gatewayId(), AnomalyRuleName.GATEWAY_OFFLINE, AnomalySeverity.WARN, message, event.at()));
        log.info("이상 발생: {} GATEWAY_OFFLINE ({})", event.gatewayId(), message);
    }

    private void resolve(GatewayConnectionChangedEvent event) {
        Anomaly open = findOpen(event.gatewayId());
        if (open == null) {
            return;
        }
        open.resolve(event.at());
        OffsetDateTime since = event.disconnectedAt() != null ? event.disconnectedAt() : open.getOpenedAt();
        log.info("이상 해결: {} GATEWAY_OFFLINE (오프라인 {}분)", event.gatewayId(), Duration.between(since, event.at()).toMinutes());
    }

    private Anomaly findOpen(String gatewayId) {
        return anomalyRepository
                .findByGatewayIdAndRuleAndStatus(gatewayId, AnomalyRuleName.GATEWAY_OFFLINE, AnomalyStatus.OPEN)
                .orElse(null);
    }
}
