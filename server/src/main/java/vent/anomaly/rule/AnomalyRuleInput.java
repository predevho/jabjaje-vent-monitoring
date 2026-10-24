package vent.anomaly.rule;

import java.util.List;
import java.util.Optional;

/**
 * 규칙에 넘기는 장비 한 대의 판단 재료.
 *
 * @param deviceId         장비 ID
 * @param model            장비 모델명. 모델별 기준을 고를 때 쓴다
 * @param targetAirflowCmh 그 장비가 있는 현장의 목표 풍량 (#5)
 * @param recent           품질이 OK인 최근 측정값. 측정 시각이 늦은 것부터. 늦은 측정값도 들어 있다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record AnomalyRuleInput(String deviceId, String model, int targetAirflowCmh, List<AnomalyRuleTelemetry> recent) {

    /** 측정 시각 기준 가장 최신 측정값. 늦은 측정값일 수도 있다. */
    public Optional<AnomalyRuleTelemetry> latest() {
        return recent.isEmpty() ? Optional.empty() : Optional.of(recent.get(0));
    }

    /**
     * 늦은 측정값을 뺀 최근 측정값(최신 먼저). 지금 상태를 나타내는 규칙은 이것만 쓴다 (스펙 6.2, ADR-0003).
     *
     * @since 2026-10-24 (#5)
     */
    public List<AnomalyRuleTelemetry> live() {
        return recent.stream().filter(telemetry -> !telemetry.late()).toList();
    }
}
