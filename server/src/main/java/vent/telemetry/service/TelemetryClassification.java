package vent.telemetry.service;

import vent.telemetry.domain.type.TelemetryQuality;

/**
 * 측정값 품질 판정 결과.
 *
 * @param quality 품질 (정상, 범위 밖, 시계 오차)
 * @param late    늦은 측정값인지. 품질과 따로 판정한다(늦게 왔어도 값은 정상일 수 있다)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
public record TelemetryClassification(TelemetryQuality quality, boolean late) {
}
