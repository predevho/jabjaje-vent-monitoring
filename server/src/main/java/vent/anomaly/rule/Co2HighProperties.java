package vent.anomaly.rule;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CO2 과다 규칙 기준 ({@code vent.anomaly.co2-high.*}, 스펙 6.1, #5).
 *
 * @param raisePpm    이 값을 넘는 상태가 지속되면 발생 (기본 1,000ppm)
 * @param criticalPpm 최신 값이 이 값을 넘으면 심각 (기본 1,500ppm)
 * @param clearPpm    이 값 미만인 상태가 지속되면 해제 (기본 900ppm). 발생 기준보다 낮게 둔다(히스테리시스)
 * @param lasting     발생·해제에 필요한 지속 시간 (기본 10분). 시연 때 줄인다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@ConfigurationProperties(prefix = "vent.anomaly.co2-high")
public record Co2HighProperties(
        @DefaultValue("1000") int raisePpm,
        @DefaultValue("1500") int criticalPpm,
        @DefaultValue("900") int clearPpm,
        @DefaultValue("10m") Duration lasting) {
}
