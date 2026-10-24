package vent.anomaly.rule;

import java.time.OffsetDateTime;
import vent.anomaly.domain.type.AnomalySeverity;

/**
 * 규칙 판단 결과: 발생, 해제, 그대로 중 하나.
 *
 * <p>규칙은 지금 열린 이상이 있는지 모른다. 중복 방지(이미 열려 있으면 다시 만들지 않기)는 서비스가 맡는다.
 *
 * @param kind     판단 종류
 * @param severity 발생일 때 심각도 (그 외 null)
 * @param message  발생일 때 화면에 보일 설명 (그 외 null)
 * @param at       판단 근거가 된 측정값의 측정 시각 (그대로일 때 null)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record AnomalyRuleOutcome(Kind kind, AnomalySeverity severity, String message, OffsetDateTime at) {

    /**
     * 판단 종류.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#4)
     */
    public enum Kind {
        /** 이상 조건이 맞음 */
        RAISE,
        /** 해제 조건이 맞음 */
        CLEAR,
        /** 발생도 해제도 아님. 지금 상태를 그대로 둔다 */
        UNCHANGED
    }

    public static AnomalyRuleOutcome raise(AnomalySeverity severity, String message, OffsetDateTime at) {
        return new AnomalyRuleOutcome(Kind.RAISE, severity, message, at);
    }

    public static AnomalyRuleOutcome clear(OffsetDateTime at) {
        return new AnomalyRuleOutcome(Kind.CLEAR, null, null, at);
    }

    public static AnomalyRuleOutcome unchanged() {
        return new AnomalyRuleOutcome(Kind.UNCHANGED, null, null, null);
    }
}
