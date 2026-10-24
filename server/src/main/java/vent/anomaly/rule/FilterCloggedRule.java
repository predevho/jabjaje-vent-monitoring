package vent.anomaly.rule;

import java.time.Duration;
import org.springframework.stereotype.Component;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.site.domain.type.DeviceType;

/**
 * 필터 막힘 규칙 ({@code FILTER_CLOGGED}, 스펙 6.1).
 *
 * <p>측정 시각 기준 가장 최신 측정값의 필터 차압으로 판단한다.
 * <ul>
 *   <li>발생: 차압이 모델별 기준을 넘음 → 주의(WARN)</li>
 *   <li>해제: 차압이 기준 − 여유값(기본 20Pa) 아래로 내려감 (필터 교체)</li>
 *   <li>그 사이: 지금 상태를 그대로 둔다 (히스테리시스)</li>
 * </ul>
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@Component
public class FilterCloggedRule implements DeviceAnomalyRule {

    private final FilterCloggedProperties properties;

    public FilterCloggedRule(FilterCloggedProperties properties) {
        this.properties = properties;
    }

    @Override
    public AnomalyRuleName name() {
        return AnomalyRuleName.FILTER_CLOGGED;
    }

    /** 최신 값 하나만 본다. */
    @Override
    public Duration lookback() {
        return Duration.ZERO;
    }

    @Override
    public boolean appliesTo(DeviceType type) {
        return type == DeviceType.VENTILATOR;
    }

    @Override
    public AnomalyRuleOutcome evaluate(AnomalyRuleInput input) {
        return input.latest()
                .map(latest -> judge(input.model(), latest))
                .orElse(AnomalyRuleOutcome.unchanged());
    }

    private AnomalyRuleOutcome judge(String model, AnomalyRuleTelemetry latest) {
        int threshold = properties.thresholdFor(model);
        if (latest.filterPa() > threshold) {
            return AnomalyRuleOutcome.raise(AnomalySeverity.WARN,
                    "필터 차압 %dPa (기준 %dPa 초과). 필터 교체가 필요합니다".formatted(latest.filterPa(), threshold),
                    latest.measuredAt());
        }
        if (latest.filterPa() < threshold - properties.resolveMarginPa()) {
            return AnomalyRuleOutcome.clear(latest.measuredAt());
        }
        return AnomalyRuleOutcome.unchanged();
    }
}
