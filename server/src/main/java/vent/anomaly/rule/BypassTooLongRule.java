package vent.anomaly.rule;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.site.domain.type.DeviceType;

/**
 * 바이패스 지속 규칙 ({@code BYPASS_TOO_LONG}, 스펙 6.1).
 *
 * <ul>
 *   <li>발생: 바이패스가 60분 넘게 이어짐 → 주의(WARN). 바이패스가 오래 이어지면 장비가 스스로 오류를 낸다(용어집)</li>
 *   <li>해제: 바이패스가 꺼진 측정값이 오면</li>
 *   <li>늦은 측정값은 쓰지 않는다 (스펙 6.2)</li>
 * </ul>
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@Component
public class BypassTooLongRule implements DeviceAnomalyRule {

    private final BypassTooLongProperties properties;
    private final AnomalyContinuityProperties continuity;

    public BypassTooLongRule(BypassTooLongProperties properties, AnomalyContinuityProperties continuity) {
        this.properties = properties;
        this.continuity = continuity;
    }

    @Override
    public AnomalyRuleName name() {
        return AnomalyRuleName.BYPASS_TOO_LONG;
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
        if (!latest.bypass()) {
            return AnomalyRuleOutcome.clear(latest.measuredAt());
        }
        boolean tooLong = AnomalyRuleStreak.lastedLongerThan(live, AnomalyRuleTelemetry::bypass,
                continuity.maxGap(), properties.lasting());
        if (tooLong) {
            String message = "바이패스가 %s 넘게 켜져 있습니다. 열교환이 되지 않으니 장비 설정을 확인하세요"
                    .formatted(AnomalyRuleStreak.describe(properties.lasting()));
            return AnomalyRuleOutcome.raise(AnomalySeverity.WARN, message, latest.measuredAt());
        }
        return AnomalyRuleOutcome.unchanged();
    }
}
