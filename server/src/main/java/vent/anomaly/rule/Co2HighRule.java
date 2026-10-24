package vent.anomaly.rule;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.site.domain.type.DeviceType;

/**
 * CO2 과다 규칙 ({@code CO2_HIGH}, 스펙 6.1).
 *
 * <ul>
 *   <li>발생: 1,000ppm 초과가 10분 이어짐 → 주의(WARN). 최신 값이 1,500ppm을 넘으면 심각(CRITICAL)</li>
 *   <li>해제: 900ppm 미만이 10분 이어짐 (히스테리시스)</li>
 *   <li>늦은 측정값은 쓰지 않는다. 몇 시간 전 값으로 지금 이상이 뜨면 안 된다 (스펙 6.2)</li>
 * </ul>
 * 이미 주의로 열려 있을 때 심각이 나오면 서비스가 심각도를 올린다. 심각도 판단도 1,000ppm 초과가 이어지는 동안에만 한다.
 * 그래서 주의로 열린 뒤 CO2가 잠깐 1,000ppm 아래로 내려갔다 다시 오르면, 다시 이어진 시간이 기준을 채운 뒤에 심각이 된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@Component
public class Co2HighRule implements DeviceAnomalyRule {

    private final Co2HighProperties properties;
    private final AnomalyContinuityProperties continuity;

    public Co2HighRule(Co2HighProperties properties, AnomalyContinuityProperties continuity) {
        this.properties = properties;
        this.continuity = continuity;
    }

    @Override
    public AnomalyRuleName name() {
        return AnomalyRuleName.CO2_HIGH;
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
        if (live.isEmpty()) {
            return AnomalyRuleOutcome.unchanged();
        }
        AnomalyRuleTelemetry latest = live.get(0);

        boolean high = AnomalyRuleStreak.lastedAtLeast(live, t -> t.co2Ppm() > properties.raisePpm(),
                continuity.maxGap(), properties.lasting());
        if (high) {
            AnomalySeverity severity = latest.co2Ppm() > properties.criticalPpm() ? AnomalySeverity.CRITICAL : AnomalySeverity.WARN;
            String message = "CO2 %,dppm (%,dppm 초과가 %s 이상 이어짐). 환기량과 재실 인원을 확인하세요"
                    .formatted(latest.co2Ppm(), properties.raisePpm(), AnomalyRuleStreak.describe(properties.lasting()));
            return AnomalyRuleOutcome.raise(severity, message, latest.measuredAt());
        }

        boolean low = AnomalyRuleStreak.lastedAtLeast(live, t -> t.co2Ppm() < properties.clearPpm(),
                continuity.maxGap(), properties.lasting());
        return low ? AnomalyRuleOutcome.clear(latest.measuredAt()) : AnomalyRuleOutcome.unchanged();
    }
}
