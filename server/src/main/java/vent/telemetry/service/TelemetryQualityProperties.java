package vent.telemetry.service;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 측정값 품질 판정 기준 ({@code vent.telemetry.quality.*}).
 *
 * <p>기본값은 application.yml에 있다. 현장이나 모델마다 다르게 쓸 수 있도록 설정으로 뺐다.
 *
 * @param lateThreshold      수신 시각 − 측정 시각이 이 값을 넘으면 늦은 측정값
 * @param clockSkewTolerance 측정 시각 − 수신 시각이 이 값을 넘으면 시계 오차
 * @param co2Ppm             CO2 정상 범위 (ppm)
 * @param tempC              온도 정상 범위 (℃)
 * @param humidityPct        습도 정상 범위 (%)
 * @param airflowCmh         급기·배기 정상 범위 (CMH)
 * @param filterPa           필터 차압 정상 범위 (Pa)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
@ConfigurationProperties(prefix = "vent.telemetry.quality")
public record TelemetryQualityProperties(
        Duration lateThreshold,
        Duration clockSkewTolerance,
        Range co2Ppm,
        Range tempC,
        Range humidityPct,
        Range airflowCmh,
        Range filterPa) {

    /**
     * 측정 항목 하나의 정상 범위 (양 끝 포함).
     *
     * @param min 최솟값
     * @param max 최댓값
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#3)
     */
    public record Range(double min, double max) {

        public boolean contains(double value) {
            return value >= min && value <= max;
        }

        public boolean contains(BigDecimal value) {
            return contains(value.doubleValue());
        }
    }
}
