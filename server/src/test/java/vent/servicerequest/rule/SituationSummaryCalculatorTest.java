package vent.servicerequest.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상황 요약 계산 단위 테스트.
 *
 * <p>테스트 지점: 계산 클래스의 공개 메서드. 기준은 스펙 8.2(숫자 계산은 코드가 한다)와 #10에서 정한 지표이고,
 * 기대값은 손으로 계산한 숫자다. 7일, 30초 주기면 받았어야 할 측정값은 20,160개다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
class SituationSummaryCalculatorTest {

    private static final Duration WEEK = Duration.ofDays(7);
    private static final Duration INTERVAL = Duration.ofSeconds(30);
    private static final SituationSiteProfile SITE = new SituationSiteProfile("경기 안성시", new BigDecimal("99.0"), 3, 149, 149,
            List.of(new SituationDevice("VENTILATOR", "DOMEKT R 300V"), new SituationDevice("VEMC", "VEMC")));
    private static final SituationHoodStats NO_HOOD = new SituationHoodStats(0, 0);

    private final SituationSummaryCalculator calculator = new SituationSummaryCalculator();

    @Test
    @DisplayName("측정값 통계로 CO2 평균·최대, 목표 풍량 대비 급기 비율, 필터 차압 추세, 바이패스 시간·비율, 데이터 충분도를 계산한다")
    void calculatesTelemetryMetrics() {
        SituationSummary summary = calculator.summarize(
                stats(new SituationTelemetryStats(20_160, 812.6, 1_430, 136.5, 2_016, 82.4, 141), NO_HOOD, 0));

        assertThat(summary.co2AvgPpm()).isEqualTo(813);
        assertThat(summary.co2MaxPpm()).isEqualTo(1_430);
        // 136.5 ÷ 149 = 0.916…
        assertThat(summary.supplyToTargetRatio()).isEqualTo(0.92);
        assertThat(summary.filterPaTrend()).isEqualTo(new SituationFilterPaTrend(82, 141, 59));
        // 2,016개 × 30초 = 16.8시간, 2,016 ÷ 20,160 = 10%
        assertThat(summary.bypassHours()).isEqualTo(16.8);
        assertThat(summary.bypassShare()).isEqualTo(10.0);
        assertThat(summary.dataCoverage()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("후드 연동 성공률은 정상 ÷ (정상 + 무반응)이고, 확인 보류는 세지 않는다")
    void hoodInterlockSuccessRate() {
        SituationSummary summary = calculator.summarize(
                stats(new SituationTelemetryStats(20_160, 800.0, 900, 145.0, 0, 80.0, 82), new SituationHoodStats(3, 1), 0));

        assertThat(summary.hoodInterlockSuccessRate()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("측정값이 적으면 데이터 충분도가 낮고, 분모가 0인 지표는 비워 둔다 (AI가 판단을 보류할 근거)")
    void sparseDataLeavesMetricsEmpty() {
        SituationSummary summary = calculator.summarize(
                stats(new SituationTelemetryStats(2_016, null, null, null, 0, null, null), NO_HOOD, 4));

        assertThat(summary.dataCoverage()).isEqualTo(0.1);
        assertThat(summary.co2AvgPpm()).isNull();
        assertThat(summary.supplyToTargetRatio()).isNull();
        assertThat(summary.filterPaTrend()).isNull();
        assertThat(summary.hoodInterlockSuccessRate()).isNull();
        assertThat(summary.bypassHours()).isEqualTo(0.0);
        assertThat(summary.offlineCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("측정값이 하나도 없으면 데이터 충분도 0, 바이패스 시간·비율도 비워 둔다")
    void noTelemetryAtAll() {
        SituationSummary summary = calculator.summarize(stats(SituationTelemetryStats.NONE, NO_HOOD, 0));

        assertThat(summary.dataCoverage()).isEqualTo(0.0);
        assertThat(summary.bypassHours()).isNull();
        assertThat(summary.bypassShare()).isNull();
    }

    @Test
    @DisplayName("근거로 쓸 수 있는 지표는 값이 있는 것뿐이다 (데이터 없는 근거를 막는다)")
    void availableMetricKeysOnlyWithValues() {
        SituationSummary summary = calculator.summarize(
                stats(new SituationTelemetryStats(2_016, 800.0, 900, null, 0, null, null), NO_HOOD, 0));

        assertThat(summary.availableMetricKeys())
                .containsExactlyInAnyOrder("co2AvgPpm", "co2MaxPpm", "bypassHours", "bypassShare", "offlineCount", "dataCoverage");
    }

    private static SituationStats stats(SituationTelemetryStats telemetry, SituationHoodStats hood, long offlineCount) {
        return new SituationStats(SITE, WEEK, INTERVAL, telemetry, hood, offlineCount, List.of(), List.of());
    }
}
