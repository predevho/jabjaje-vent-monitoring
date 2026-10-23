package vent.simulator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 게이트웨이가 보내는 측정값 메시지 (스펙 4.2).
 *
 * <p>서버의 {@code vent.telemetry.dto.request.TelemetryMessage}와 같은 형식이다. 형식을 바꿀 때는 필드 추가만 한다(ADR-0004).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public record TelemetryMessage(
        int schemaVersion,
        UUID messageId,
        String deviceId,
        OffsetDateTime measuredAt,
        int co2Ppm,
        BigDecimal tempC,
        BigDecimal humidityPct,
        int supplyCmh,
        int exhaustCmh,
        int filterPa,
        boolean bypass) {

    /** 현재 메시지 형식 버전. */
    public static final int SCHEMA_VERSION = 1;
}
