package vent.anomaly.rule;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * "N분 지속" 규칙이 함께 쓰는 기준 ({@code vent.anomaly.continuity.*}, #5).
 *
 * @param maxGap 측정값 사이가 이 시간보다 길게 비면 이어진 구간이 끊긴 것으로 본다 (기본 2분, 일시 끊김 기준과 같다)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@ConfigurationProperties(prefix = "vent.anomaly.continuity")
public record AnomalyContinuityProperties(@DefaultValue("2m") Duration maxGap) {
}
