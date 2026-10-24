package vent.anomaly.rule;

import java.time.Duration;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.site.domain.type.DeviceType;

/**
 * 장비 측정값으로 판단하는 이상 규칙.
 *
 * <p>규칙 하나를 클래스 하나로 만든다. 규칙은 DB도 지금 열린 이상도 모르는 순수한 판단 코드이고,
 * 저장과 중복 방지는 {@code AnomalyEvaluationService}가 맡는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public interface DeviceAnomalyRule {

    /** 이 규칙의 이름. 열린 이상을 찾을 때 대상과 함께 쓴다. */
    AnomalyRuleName name();

    /** 이 규칙을 평가할 장비 종류인지. 예: 필터 막힘은 환기장치에만 해당하고 VEMC에는 해당하지 않는다. */
    boolean appliesTo(DeviceType type);

    /**
     * 판단하려면 최신 측정값에서 얼마나 거슬러 올라간 측정값까지 필요한지. 서비스는 규칙들 중 가장 긴 만큼 가져온다.
     * 최신 값 하나만 보는 규칙은 {@link Duration#ZERO}.
     *
     * @since 2026-10-24 (#5)
     */
    Duration lookback();

    /** 장비 한 대의 측정값으로 발생, 해제, 그대로 중 하나를 판단한다. */
    AnomalyRuleOutcome evaluate(AnomalyRuleInput input);
}
