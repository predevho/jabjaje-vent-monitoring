package vent.anomaly.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.site.domain.type.DeviceType;

/**
 * 필터 막힘 규칙 단위 테스트.
 *
 * <p>테스트 지점: 규칙의 공개 메서드. 기준은 #4에서 정한 값(발생: 기준 초과, 해제: 기준 − 20Pa 미만, 모델별 기준, 모르는 모델은 기본값)이고,
 * 경계값을 직접 적은 숫자로 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
class FilterCloggedRuleTest {

    private static final OffsetDateTime MEASURED_AT = OffsetDateTime.of(2026, 10, 24, 1, 0, 0, 0, ZoneOffset.UTC);

    /** R 300V는 150Pa, R 450V는 170Pa로 일부러 다르게 둬서 모델별 기준을 확인한다. 기본값은 150Pa. */
    private final FilterCloggedRule rule = new FilterCloggedRule(new FilterCloggedProperties(
            150, 20, Map.of("DOMEKT R 300V", 150, "DOMEKT R 450V", 170)));

    @Test
    @DisplayName("차압이 기준(150Pa)을 넘으면 WARN으로 발생한다")
    void raisesAboveThreshold() {
        AnomalyRuleOutcome outcome = rule.evaluate(readings("DOMEKT R 300V", 151));

        assertThat(outcome.kind()).isEqualTo(AnomalyRuleOutcome.Kind.RAISE);
        assertThat(outcome.severity()).isEqualTo(AnomalySeverity.WARN);
        assertThat(outcome.at()).isEqualTo(MEASURED_AT);
    }

    @Test
    @DisplayName("정확히 기준(150Pa)이면 발생하지 않는다")
    void exactlyThresholdDoesNotRaise() {
        assertThat(rule.evaluate(readings("DOMEKT R 300V", 150)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.UNCHANGED);
    }

    @Test
    @DisplayName("기준 − 20Pa(130Pa) 미만이면 해제한다")
    void clearsBelowResolveLevel() {
        assertThat(rule.evaluate(readings("DOMEKT R 300V", 129)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.CLEAR);
    }

    @Test
    @DisplayName("130~150Pa 사이에서는 지금 상태를 그대로 둔다(켜졌다 꺼졌다 반복 방지)")
    void keepsStateBetweenResolveLevelAndThreshold() {
        assertThat(rule.evaluate(readings("DOMEKT R 300V", 130)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.UNCHANGED);
        assertThat(rule.evaluate(readings("DOMEKT R 300V", 140)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.UNCHANGED);
    }

    @Test
    @DisplayName("모델마다 기준이 다르다: R 450V는 170Pa를 넘어야 발생한다")
    void usesModelSpecificThreshold() {
        assertThat(rule.evaluate(readings("DOMEKT R 450V", 160)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.UNCHANGED);
        assertThat(rule.evaluate(readings("DOMEKT R 450V", 171)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.RAISE);
    }

    @Test
    @DisplayName("기준이 없는 모델은 기본값(150Pa)을 쓴다")
    void unknownModelUsesDefaultThreshold() {
        assertThat(rule.evaluate(readings("UNKNOWN", 151)).kind()).isEqualTo(AnomalyRuleOutcome.Kind.RAISE);
    }

    @Test
    @DisplayName("판단할 측정값이 없으면 아무것도 바꾸지 않는다")
    void noReadingsLeavesStateUnchanged() {
        assertThat(rule.evaluate(new AnomalyRuleInput("vent-s1", "DOMEKT R 300V", 200, List.of())).kind())
                .isEqualTo(AnomalyRuleOutcome.Kind.UNCHANGED);
    }

    @Test
    @DisplayName("환기장치에만 해당하고 VEMC에는 해당하지 않는다")
    void appliesToVentilatorOnly() {
        assertThat(rule.appliesTo(DeviceType.VENTILATOR)).isTrue();
        assertThat(rule.appliesTo(DeviceType.VEMC)).isFalse();
    }

    private static AnomalyRuleInput readings(String model, int filterPa) {
        return new AnomalyRuleInput("vent-s1", model, 200,
                List.of(new AnomalyRuleTelemetry(MEASURED_AT, 800, 200, filterPa, false, false, false)));
    }
}
