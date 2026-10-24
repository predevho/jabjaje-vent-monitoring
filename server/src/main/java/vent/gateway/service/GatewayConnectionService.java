package vent.gateway.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vent.gateway.domain.Gateway;
import vent.gateway.domain.type.ConnectionState;
import vent.gateway.dto.request.GatewayStatusMessage;
import vent.gateway.repository.GatewayRepository;

/**
 * 게이트웨이 연결 상태 유스케이스.
 *
 * <p>세 곳에서 상태가 바뀐다(스펙 6.3).
 * <ul>
 *   <li>측정값 저장, retained가 아닌 online 보고 → 연결됨</li>
 *   <li>offline 보고(Last Will) → 일시 끊김</li>
 *   <li>30초 주기 무수신 검사 → 일시 끊김, 오프라인</li>
 * </ul>
 * 상태를 바꾸기 전에 게이트웨이 행을 잠그고, 바뀌면 같은 트랜잭션에서 {@link GatewayConnectionChangedEvent}를 발행한다.
 * 오프라인 이상은 그 이벤트를 받아 같은 트랜잭션에서 열고 닫는다(ADR-0014).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@Service
public class GatewayConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GatewayConnectionService.class);
    private static final EnumSet<ConnectionState> CHECKED_STATES = EnumSet.of(ConnectionState.CONNECTED, ConnectionState.INTERRUPTED);

    private final GatewayRepository gatewayRepository;
    private final GatewayConnectionProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public GatewayConnectionService(GatewayRepository gatewayRepository, GatewayConnectionProperties properties,
                                    ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.gatewayRepository = gatewayRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 이 게이트웨이를 통해 측정값이 저장됐다. 측정값 저장 트랜잭션이 커밋된 뒤 불리므로 새 트랜잭션에서 실행한다.
     *
     * @param gatewayId  게이트웨이 ID
     * @param receivedAt 서버가 측정값을 받은 시각
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReceived(String gatewayId, OffsetDateTime receivedAt) {
        gatewayRepository.findByIdForUpdate(gatewayId)
                .ifPresent(gateway -> applyTransition(gateway, receivedAt, g -> g.markReceived(receivedAt)));
    }

    /**
     * 상태 토픽 메시지를 반영한다.
     *
     * <p>retained로 온 online은 서버가 다시 구독할 때 브로커가 보내 주는 옛날 메시지라, 버전과 지원 기능만 기록하고
     * 연결 상태는 바꾸지 않는다. offline은 retained여도 반영한다. 서버가 꺼진 동안 Last Will이 나갔다는 뜻이다.
     *
     * @param topicSiteId    토픽의 현장 ID
     * @param topicGatewayId 토픽의 게이트웨이 ID
     * @param message        상태 메시지
     * @param retained       브로커가 보관하던 메시지인지
     * @param receivedAt     서버가 받은 시각
     */
    @Transactional
    public GatewayStatusResult applyStatus(String topicSiteId, String topicGatewayId, GatewayStatusMessage message,
                                           boolean retained, OffsetDateTime receivedAt) {
        if (!message.isOnline() && !message.isOffline()) {
            return GatewayStatusResult.INVALID;
        }
        Optional<Gateway> found = gatewayRepository.findByIdForUpdate(topicGatewayId);
        if (found.isEmpty()) {
            return GatewayStatusResult.UNKNOWN_GATEWAY;
        }
        Gateway gateway = found.get();
        if (!gateway.belongsTo(topicSiteId)) {
            return GatewayStatusResult.SITE_MISMATCH;
        }

        if (message.isOnline()) {
            gateway.recordReport(message.firmwareVersion(), message.capabilities());
            if (!retained) {
                applyTransition(gateway, receivedAt, g -> g.markReceived(receivedAt));
            }
        } else {
            applyTransition(gateway, receivedAt, Gateway::markReportedOffline);
        }
        return GatewayStatusResult.APPLIED;
    }

    /**
     * 무수신 검사. 연결됨·일시 끊김 게이트웨이를 하나씩 잠가서 다시 읽고, 기준 시간을 넘긴 것의 상태를 바꾼다.
     *
     * <p>게이트웨이마다 트랜잭션을 따로 쓴다. 한 게이트웨이의 이상 저장이 실패해도 다른 게이트웨이의 상태 변경은
     * 롤백되지 않고, 실패한 것은 다음 검사(30초 뒤)에서 다시 시도된다. 잠금도 짧게 잡힌다.
     *
     * @param now 검사 시각. 스케줄러는 지금 시각을, 테스트는 원하는 시각을 넣는다
     */
    public void checkSilence(OffsetDateTime now) {
        for (String gatewayId : gatewayRepository.findIdsByConnectionStateIn(CHECKED_STATES)) {
            try {
                transactionTemplate.executeWithoutResult(status -> gatewayRepository.findByIdForUpdate(gatewayId)
                        .ifPresent(gateway -> applyTransition(gateway, now,
                                g -> g.checkSilence(now, properties.interruptedAfter(), properties.offlineAfter()))));
            } catch (RuntimeException e) {
                log.error("게이트웨이 무수신 검사 실패, 다음 검사에서 다시 시도: {}", gatewayId, e);
            }
        }
    }

    /** 상태 변경을 적용하고, 상태가 바뀌었으면 이벤트를 발행한다. */
    private void applyTransition(Gateway gateway, OffsetDateTime at, Consumer<Gateway> transition) {
        ConnectionState before = gateway.getConnectionState();
        OffsetDateTime disconnectedBefore = gateway.getDisconnectedAt();
        transition.accept(gateway);
        ConnectionState after = gateway.getConnectionState();
        if (before == after) {
            return;
        }
        OffsetDateTime disconnectedAt = gateway.getDisconnectedAt() != null ? gateway.getDisconnectedAt() : disconnectedBefore;
        log.info("게이트웨이 연결 상태: {} {} → {}", gateway.getId(), before, after);
        eventPublisher.publishEvent(new GatewayConnectionChangedEvent(gateway.getId(), before, after, at, disconnectedAt));
    }
}
