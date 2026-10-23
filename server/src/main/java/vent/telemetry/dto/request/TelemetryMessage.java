package vent.telemetry.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 게이트웨이가 MQTT로 보내는 측정값 메시지 (스펙 4.2).
 *
 * <p>모르는 필드는 무시한다(ADR-0004의 "필드 추가만" 원칙). 장비 ID는 토픽 값을 믿고, 이 값은 토픽과 같은지 확인하는 데만 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public record TelemetryMessage(
        Integer schemaVersion,
        UUID messageId,
        String deviceId,
        OffsetDateTime measuredAt,
        Integer co2Ppm,
        BigDecimal tempC,
        BigDecimal humidityPct,
        Integer supplyCmh,
        Integer exhaustCmh,
        Integer filterPa,
        Boolean bypass) {

    /** 저장에 필요한 값이 모두 있는지 확인한다. 값의 범위 검증은 #3에서 다룬다. */
    public boolean hasAllRequiredFields() {
        return Stream.of(messageId, deviceId, measuredAt, co2Ppm, tempC, humidityPct,
                        supplyCmh, exhaustCmh, filterPa, bypass)
                .allMatch(value -> value != null);
    }
}
