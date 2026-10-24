package vent.anomaly.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static vent.anomaly.rule.TelemetrySeries.bypass;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.anomaly.rule.AnomalyRuleOutcome.Kind;

/**
 * 바이패스 지속 규칙 단위 테스트.
 *
 * <p>테스트 지점: 규칙의 공개 메서드. 기준은 스펙 6.1(바이패스 60분 넘게 → WARN, 바이패스 꺼짐 → 해제)이다.
 * 30초 간격 121개가 60분이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
class BypassTooLongRuleTest {

    private final BypassTooLongRule rule = new BypassTooLongRule(
            new BypassTooLongProperties(Duration.ofMinutes(60)),
            new AnomalyContinuityProperties(Duration.ofMinutes(2)));

    @Test
    @DisplayName("바이패스가 60분을 넘으면 발생, 딱 60분이면 아직 아니다 (스펙: 60분 넘게)")
    void raisesAfterMoreThanSixtyMinutes() {
        assertThat(evaluate(TelemetrySeries.start().then(122, bypass(true))).kind()).isEqualTo(Kind.RAISE);
        assertThat(evaluate(TelemetrySeries.start().then(121, bypass(true))).kind()).isEqualTo(Kind.UNCHANGED);
    }

    @Test
    @DisplayName("바이패스가 꺼지면 해제한다")
    void clearsWhenBypassOff() {
        assertThat(evaluate(TelemetrySeries.start().then(122, bypass(true)).then(1, bypass(false))).kind())
                .isEqualTo(Kind.CLEAR);
    }

    @Test
    @DisplayName("늦은 측정값은 쓰지 않는다")
    void ignoresLateTelemetry() {
        assertThat(evaluate(TelemetrySeries.start().then(122, bypass(true).asLate())).kind()).isEqualTo(Kind.UNCHANGED);
    }

    private AnomalyRuleOutcome evaluate(TelemetrySeries series) {
        return rule.evaluate(new AnomalyRuleInput("vent-s1", "DOMEKT R 300V", 200, series.build()));
    }
}
