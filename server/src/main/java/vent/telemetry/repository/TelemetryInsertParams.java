package vent.telemetry.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 측정값 INSERT 파라미터.
 *
 * <p>엔티티가 아니라 {@link TelemetryInsertRepository}에 넘기는 값 묶음이라 repository 레이어에 둔다.
 * 수신한 메시지에서 검증을 마친 값과 서버 수신 시각을 담는다. 구성 요소 이름이 INSERT SQL의 이름 붙은 파라미터와 같다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public record TelemetryInsertParams(
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
}
