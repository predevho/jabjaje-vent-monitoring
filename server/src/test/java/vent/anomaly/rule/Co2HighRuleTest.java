package vent.anomaly.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static vent.anomaly.rule.TelemetrySeries.co2;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.rule.AnomalyRuleOutcome.Kind;

/**
 * CO2 과다 규칙 단위 테스트.
 *
 * <p>테스트 지점: 규칙의 공개 메서드. 기준은 스펙 6.1(1,000ppm 초과 10분 → WARN, 1,500ppm 초과 → CRITICAL,
 * 900ppm 미만 10분 → 해제)이고, 30초 간격 측정값 21개가 10분이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
class Co2HighRuleTest {

    private final Co2HighRule rule = new Co2HighRule(
            new Co2HighProperties(1000, 1500, 900, Duration.ofMinutes(10)),
            new AnomalyContinuityProperties(Duration.ofMinutes(2)));

    @Test
    @DisplayName("1,000ppm 초과가 10분 이어지면 주의로 발생한다")
    void raisesWarnAfterTenMinutesAbove() {
        AnomalyRuleOutcome outcome = evaluate(TelemetrySeries.start().then(5, co2(800)).then(21, co2(1001)));

        assertThat(outcome.kind()).isEqualTo(Kind.RAISE);
        assertThat(outcome.severity()).isEqualTo(AnomalySeverity.WARN);
        assertThat(outcome.at()).isEqualTo(TelemetrySeries.T0);
    }

    @Test
    @DisplayName("9분 30초만 이어지면 아직 발생하지 않는다")
    void notRaisedBeforeTenMinutes() {
        assertThat(evaluate(TelemetrySeries.start().then(5, co2(800)).then(20, co2(1001))).kind())
                .isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("1,000ppm 정각은 초과가 아니다")
    void exactlyThresholdIsNotAbove() {
        assertThat(evaluate(TelemetrySeries.start().then(21, co2(1000))).kind()).isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("10분 이어졌고 최신 값이 1,500ppm을 넘으면 심각이다")
    void raisesCriticalAboveCriticalThreshold() {
        AnomalyRuleOutcome outcome = evaluate(TelemetrySeries.start().then(20, co2(1200)).then(1, co2(1501)));

        assertThat(outcome.kind()).isEqualTo(Kind.RAISE);
        assertThat(outcome.severity()).isEqualTo(AnomalySeverity.CRITICAL);
    }

    @Test
    @DisplayName("900ppm 미만이 10분 이어지면 해제, 900ppm 정각이면 그대로 (히스테리시스)")
    void clearsBelowClearThresholdOnly() {
        assertThat(evaluate(TelemetrySeries.start().then(21, co2(899))).kind()).isEqualTo(Kind.CLEAR);
        assertThat(evaluate(TelemetrySeries.start().then(21, co2(900))).kind()).isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("중간에 1,000ppm 이하가 하나 끼면 그 뒤부터 다시 센다")
    void dipRestartsCount() {
        assertThat(evaluate(TelemetrySeries.start().then(15, co2(1100)).then(1, co2(990)).then(15, co2(1100))).kind())
                .isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("늦은 측정값은 쓰지 않는다: 최근 값이 모두 늦은 측정값이면 판단하지 않는다")
    void ignoresLateTelemetry() {
        assertThat(evaluate(TelemetrySeries.start().then(21, co2(1100).asLate())).kind()).isEqualTo(Kind.UNCHANGED);
        // 늦은 측정값을 빼면 실시간 값은 5개(2분)뿐이다.
        assertThat(evaluate(TelemetrySeries.start().then(16, co2(1100).asLate()).then(5, co2(1100))).kind())
                .isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("측정값이 2분 넘게 비면 구간이 끊긴다")
    void gapBreaksStreak() {
        // 앞 10개(4분 30초) + 3분 빈 구간 + 뒤 12개(5분 30초) = 13분이지만 이어지지 않았다.
        assertThat(evaluate(TelemetrySeries.start().then(10, co2(1100)).gapSeconds(180).then(12, co2(1100))).kind())
                .isEqualTo(Kind.UNCHANGED);
    }

    private AnomalyRuleOutcome evaluate(TelemetrySeries series) {
        return rule.evaluate(new AnomalyRuleInput("vent-s1", "DOMEKT R 300V", 200, series.build()));
    }
}
