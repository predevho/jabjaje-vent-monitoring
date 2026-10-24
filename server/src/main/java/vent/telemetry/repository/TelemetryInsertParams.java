package vent.telemetry.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 측정값 INSERT 파라미터.
 *
 * <p>엔티티가 아니라 {@link TelemetryInsertRepository}에 넘기는 값 묶음이라 repository 레이어에 둔다.
 * 수신한 메시지에서 검증을 마친 값, 서버 수신 시각, 품질 판정 결과를 담는다. 구성 요소 이름이 INSERT SQL의 이름 붙은 파라미터와 같다.
 *
 * @param quality 품질 이름(OK, OUT_OF_RANGE, CLOCK_SKEW). JDBC가 enum을 바로 넘기지 못해 문자열로 둔다
 * @param boost   부스트 중인지 (#8)
 * @param late    늦은 측정값인지
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
        boolean bypass,
        boolean boost,
        String quality,
        boolean late) {
}
