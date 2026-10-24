package vent.anomaly.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static vent.anomaly.rule.TelemetrySeries.supply;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.rule.AnomalyRuleOutcome.Kind;

/**
 * 목표 풍량 미달 규칙 단위 테스트.
 *
 * <p>테스트 지점: 규칙의 공개 메서드. 기준은 스펙 6.1(급기가 목표 풍량의 80% 미만으로 30분 → WARN, 80%에 다시 도달 → 해제,
 * 바이패스 중 제외)이다. 목표 풍량 200CMH라 80%는 160CMH이고, 30초 간격 61개가 30분이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
class AirflowBelowTargetRuleTest {

    private static final int TARGET_CMH = 200;

    private final AirflowBelowTargetRule rule = new AirflowBelowTargetRule(
            new AirflowBelowTargetProperties(0.8, Duration.ofMinutes(30)),
            new AnomalyContinuityProperties(Duration.ofMinutes(2)));

    @Test
    @DisplayName("급기가 80% 미만으로 30분 이어지면 주의로 발생한다")
    void raisesAfterThirtyMinutesBelow() {
        AnomalyRuleOutcome outcome = evaluate(TelemetrySeries.start().then(61, supply(159)));

        assertThat(outcome.kind()).isEqualTo(Kind.RAISE);
        assertThat(outcome.severity()).isEqualTo(AnomalySeverity.WARN);
    }

    @Test
    @DisplayName("29분 30초만 이어지면 아직 발생하지 않는다")
    void notRaisedBeforeThirtyMinutes() {
        assertThat(evaluate(TelemetrySeries.start().then(60, supply(159))).kind()).isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("정확히 80%면 미달이 아니고, 이상이 열려 있으면 해제한다")
    void eightyPercentIsNotBelowAndClears() {
        assertThat(evaluate(TelemetrySeries.start().then(61, supply(160))).kind()).isEqualTo(Kind.CLEAR);
    }

    @Test
    @DisplayName("80%에 다시 도달한 측정값이 하나만 와도 해제한다")
    void singleReadingAtTargetClears() {
        assertThat(evaluate(TelemetrySeries.start().then(61, supply(150)).then(1, supply(170))).kind())
                .isEqualTo(Kind.CLEAR);
    }

    @Test
    @DisplayName("바이패스 중인 측정값으로는 판단하지 않는다: 최신 값이 바이패스면 그대로")
    void bypassLatestLeavesUnchanged() {
        assertThat(evaluate(TelemetrySeries.start().then(61, supply(150)).then(1, supply(150).withBypass())).kind())
                .isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("중간에 바이패스가 끼면 그 뒤부터 다시 센다")
    void bypassInMiddleRestartsCount() {
        assertThat(evaluate(TelemetrySeries.start()
                .then(40, supply(150)).then(1, supply(150).withBypass()).then(40, supply(150))).kind())
                .isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("늦은 측정값은 쓰지 않는다")
    void ignoresLateTelemetry() {
        assertThat(evaluate(TelemetrySeries.start().then(61, supply(150).asLate())).kind()).isEqualTo(Kind.UNCHANGED);
    }

    private AnomalyRuleOutcome evaluate(TelemetrySeries series) {
        return rule.evaluate(new AnomalyRuleInput("vent-s1", "DOMEKT R 300V", TARGET_CMH, series.build()));
    }
}
