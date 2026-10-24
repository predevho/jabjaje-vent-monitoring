package vent.anomaly.rule;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 바이패스 지속 규칙 기준 ({@code vent.anomaly.bypass-too-long.*}, 스펙 6.1, #5).
 *
 * @param lasting 바이패스가 이 시간보다 오래 이어지면 발생 (기본 60분). 시연 때 줄인다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@ConfigurationProperties(prefix = "vent.anomaly.bypass-too-long")
public record BypassTooLongProperties(@DefaultValue("60m") Duration lasting) {
}
