package vent.anomaly.rule;

import java.time.OffsetDateTime;

/**
 * 규칙이 보는 측정값 하나.
 *
 * <p>품질이 OK인 측정값만 들어온다. 규칙에 필요한 값만 담고, 규칙이 늘면 필드를 추가한다(#5).
 *
 * @param measuredAt 측정 시각. 판단은 이 시각 기준이다 (ADR-0003)
 * @param filterPa   필터 차압 (Pa)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record AnomalyRuleTelemetry(OffsetDateTime measuredAt, int filterPa) {
}
