package vent.anomaly.rule;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 목표 풍량 미달 규칙 기준 ({@code vent.anomaly.airflow-below-target.*}, 스펙 6.1, #5).
 *
 * @param ratio   급기가 목표 풍량의 이 비율 미만이면 미달 (기본 0.8)
 * @param lasting 발생에 필요한 지속 시간 (기본 30분). 시연 때 줄인다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@ConfigurationProperties(prefix = "vent.anomaly.airflow-below-target")
public record AirflowBelowTargetProperties(
        @DefaultValue("0.8") double ratio,
        @DefaultValue("30m") Duration lasting) {
}
