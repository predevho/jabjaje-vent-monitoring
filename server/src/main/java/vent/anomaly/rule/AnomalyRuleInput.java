package vent.anomaly.rule;

import java.util.List;
import java.util.Optional;

/**
 * 규칙에 넘기는 장비 한 대의 판단 재료.
 *
 * @param deviceId 장비 ID
 * @param model    장비 모델명. 모델별 기준을 고를 때 쓴다
 * @param recent   품질이 OK인 최근 측정값. 측정 시각이 늦은 것부터
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record AnomalyRuleInput(String deviceId, String model, List<AnomalyRuleTelemetry> recent) {

    /** 측정 시각 기준 가장 최신 측정값. */
    public Optional<AnomalyRuleTelemetry> latest() {
        return recent.isEmpty() ? Optional.empty() : Optional.of(recent.get(0));
    }
}
