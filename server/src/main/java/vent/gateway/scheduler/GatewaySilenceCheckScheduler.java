package vent.gateway.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vent.gateway.service.GatewayConnectionService;

/**
 * 게이트웨이 무수신 검사 스케줄러.
 *
 * <p>주기마다(기본 30초) 무수신 검사를 부른다. Last Will은 TCP 연결이 끊긴 것을 브로커가 알아야 나가는데,
 * Wi-Fi가 꺼져도 연결이 살아 있는 줄 아는 경우(half-open)가 있어 서버도 직접 검사한다(스펙 6.3).
 * 로직은 없고 서비스만 부른다. 주기는 {@code vent.gateway.connection.check-interval}(기본 30초)이고,
 * {@code vent.gateway.connection.check-enabled=false}면 만들지 않는다(통합 테스트는 끄고 검사를 직접 부른다).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@Component
@ConditionalOnProperty(prefix = "vent.gateway.connection", name = "check-enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySilenceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(GatewaySilenceCheckScheduler.class);

    private final GatewayConnectionService connectionService;

    public GatewaySilenceCheckScheduler(GatewayConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Scheduled(fixedDelayString = "${vent.gateway.connection.check-interval:30s}")
    public void checkSilence() {
        try {
            connectionService.checkSilence(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            // 예외가 나가도 다음 주기는 돌지만, 원인을 알 수 있게 남긴다.
            log.error("게이트웨이 무수신 검사 실패, 다음 주기에 다시 검사", e);
        }
    }
}
