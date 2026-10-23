package vent.telemetry.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import vent.telemetry.domain.Telemetry;

/**
 * 측정값 응답.
 *
 * <p>측정값 한 건과 그 측정 시각, 수신 시각.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public record TelemetryResponse(
        String deviceId,
        UUID messageId,
        OffsetDateTime measuredAt,
        OffsetDateTime receivedAt,
        int co2Ppm,
        BigDecimal tempC,
        BigDecimal humidityPct,
        int supplyCmh,
        int exhaustCmh,
        int filterPa,
        boolean bypass) {

    public static TelemetryResponse from(Telemetry telemetry) {
        return new TelemetryResponse(
                telemetry.getDeviceId(),
                telemetry.getMessageId(),
                telemetry.getMeasuredAt(),
                telemetry.getReceivedAt(),
                telemetry.getCo2Ppm(),
                telemetry.getTempC(),
                telemetry.getHumidityPct(),
                telemetry.getSupplyCmh(),
                telemetry.getExhaustCmh(),
                telemetry.getFilterPa(),
                telemetry.isBypass());
    }
}
