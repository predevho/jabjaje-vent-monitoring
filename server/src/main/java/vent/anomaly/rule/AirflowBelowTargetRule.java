package vent.anomaly.rule;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.site.domain.type.DeviceType;

/**
 * 목표 풍량 미달 규칙 ({@code AIRFLOW_BELOW_TARGET}, 스펙 6.1).
 *
 * <ul>
 *   <li>발생: 급기가 목표 풍량의 80% 미만으로 30분 이어짐 → 주의(WARN)</li>
 *   <li>해제: 급기가 80%에 다시 도달한 측정값이 오면</li>
 *   <li>바이패스 중인 측정값으로는 판단하지 않는다. 바이패스가 끼면 이어진 구간도 끊긴다</li>
 *   <li>늦은 측정값은 쓰지 않는다 (스펙 6.2)</li>
 * </ul>
 * 부스트 중 제외는 측정값에 부스트 여부가 생기는 #8에서 넣는다. 부스트는 급기를 올리므로 그 전에도 잘못 뜨지 않는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@Component
public class AirflowBelowTargetRule implements DeviceAnomalyRule {

    private final AirflowBelowTargetProperties properties;
    private final AnomalyContinuityProperties continuity;

    public AirflowBelowTargetRule(AirflowBelowTargetProperties properties, AnomalyContinuityProperties continuity) {
        this.properties = properties;
        this.continuity = continuity;
    }

    @Override
    public AnomalyRuleName name() {
        return AnomalyRuleName.AIRFLOW_BELOW_TARGET;
    }

    @Override
    public Duration lookback() {
        return properties.lasting();
    }

    @Override
    public boolean appliesTo(DeviceType type) {
        return type == DeviceType.VENTILATOR;
    }

    @Override
    public AnomalyRuleOutcome evaluate(AnomalyRuleInput input) {
        List<AnomalyRuleTelemetry> live = input.live();
        if (live.isEmpty() || live.get(0).bypass()) {
            return AnomalyRuleOutcome.unchanged();
        }
        AnomalyRuleTelemetry latest = live.get(0);
        double minimumCmh = input.targetAirflowCmh() * properties.ratio();

        boolean below = AnomalyRuleStreak.lastedAtLeast(live, t -> !t.bypass() && t.supplyCmh() < minimumCmh,
                continuity.maxGap(), properties.lasting());
        if (below) {
            int percent = (int) Math.round(latest.supplyCmh() * 100.0 / input.targetAirflowCmh());
            String message = "급기 %dCMH, 목표 풍량 %dCMH의 %d%% (%d%% 미만이 %s 동안 이어짐). 필터·덕트·팬 설정을 확인하세요"
                    .formatted(latest.supplyCmh(), input.targetAirflowCmh(), percent,
                            Math.round(properties.ratio() * 100), AnomalyRuleStreak.describe(properties.lasting()));
            return AnomalyRuleOutcome.raise(AnomalySeverity.WARN, message, latest.measuredAt());
        }
        return latest.supplyCmh() >= minimumCmh ? AnomalyRuleOutcome.clear(latest.measuredAt()) : AnomalyRuleOutcome.unchanged();
    }
}
