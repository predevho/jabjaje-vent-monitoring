package vent.anomaly.rule;

import java.time.OffsetDateTime;

/**
 * 규칙이 보는 측정값 하나.
 *
 * <p>품질이 OK인 측정값만 들어온다. 규칙에 필요한 값만 담는다.
 *
 * @param measuredAt 측정 시각. 판단은 이 시각 기준이다 (ADR-0003)
 * @param co2Ppm     CO2 (ppm, #5)
 * @param supplyCmh  급기량 (CMH, #5)
 * @param filterPa   필터 차압 (Pa)
 * @param bypass     바이패스 여부 (#5)
 * @param boost      부스트 중인지 (#8)
 * @param late       늦은 측정값인지 (#5). 지금 상태 규칙은 쓰지 않는다 (스펙 6.2)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record AnomalyRuleTelemetry(
        OffsetDateTime measuredAt,
        int co2Ppm,
        int supplyCmh,
        int filterPa,
        boolean bypass,
        boolean boost,
        boolean late) {
}
