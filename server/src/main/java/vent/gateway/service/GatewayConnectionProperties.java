package vent.gateway.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 게이트웨이 연결 상태 판단 기준 설정 ({@code vent.gateway.connection}).
 *
 * <p>시연에서는 오프라인 기준을 짧게(예: 3분) 바꿔 쓸 수 있다(#6). 검사 주기({@code check-interval})와
 * 검사를 켤지({@code check-enabled})는 스케줄러가 직접 읽는다({@code GatewaySilenceCheckScheduler}).
 *
 * @param interruptedAfter 이 시간보다 오래 받지 못하면 일시 끊김 (스펙 6.3, 기본 2분)
 * @param offlineAfter     끊긴 지 이 시간보다 오래되면 오프라인 + 이상 (기본 30분)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@ConfigurationProperties(prefix = "vent.gateway.connection")
public record GatewayConnectionProperties(
        @DefaultValue("2m") Duration interruptedAfter,
        @DefaultValue("30m") Duration offlineAfter) {
}
