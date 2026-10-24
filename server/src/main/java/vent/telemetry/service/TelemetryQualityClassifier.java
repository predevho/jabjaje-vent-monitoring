package vent.telemetry.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import vent.telemetry.domain.type.TelemetryQuality;
import vent.telemetry.dto.request.TelemetryMessage;

/**
 * 측정값 품질 판정기.
 *
 * <p>받은 측정값이 믿을 만한지(품질)와 늦게 왔는지를 판정한다(스펙 5.2). 판정 순서:
 * <ol>
 *   <li>측정 시각이 수신 시각보다 허용치를 넘게 미래면 {@code CLOCK_SKEW}. 시각을 믿을 수 없으니 늦음 판정도 하지 않는다.</li>
 *   <li>값 하나라도 정상 범위를 벗어나면 {@code OUT_OF_RANGE}.</li>
 *   <li>그 외는 {@code OK}.</li>
 * </ol>
 * 늦은 측정값은 품질과 따로, 수신 시각 − 측정 시각이 기준을 넘는지로 판정한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
@Component
public class TelemetryQualityClassifier {

    private final TelemetryQualityProperties properties;

    public TelemetryQualityClassifier(TelemetryQualityProperties properties) {
        this.properties = properties;
    }

    /**
     * 측정값 하나를 판정한다.
     *
     * @param message    필수 값이 모두 있는 측정값 메시지
     * @param receivedAt 서버가 받은 시각
     */
    public TelemetryClassification classify(TelemetryMessage message, OffsetDateTime receivedAt) {
        Duration aheadOfReceive = Duration.between(receivedAt, message.measuredAt());
        if (aheadOfReceive.compareTo(properties.clockSkewTolerance()) > 0) {
            return new TelemetryClassification(TelemetryQuality.CLOCK_SKEW, false);
        }

        boolean late = Duration.between(message.measuredAt(), receivedAt).compareTo(properties.lateThreshold()) > 0;
        TelemetryQuality quality = allValuesInRange(message) ? TelemetryQuality.OK : TelemetryQuality.OUT_OF_RANGE;
        return new TelemetryClassification(quality, late);
    }

    private boolean allValuesInRange(TelemetryMessage message) {
        return properties.co2Ppm().contains(message.co2Ppm())
                && properties.tempC().contains(message.tempC())
                && properties.humidityPct().contains(message.humidityPct())
                && properties.airflowCmh().contains(message.supplyCmh())
                && properties.airflowCmh().contains(message.exhaustCmh())
                && properties.filterPa().contains(message.filterPa());
    }
}
