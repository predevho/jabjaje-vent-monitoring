package vent.telemetry.domain.type;

/**
 * 측정값 품질.
 *
 * <p>지금 상태를 판단하는 이상 규칙과 최신값 조회는 {@link #OK}인 측정값만 쓴다(스펙 5.2, 6.2).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
public enum TelemetryQuality {
    /** 정상 */
    OK,
    /** 센서 고장값처럼 범위를 벗어난 값이 있음 */
    OUT_OF_RANGE,
    /** 측정 시각이 수신 시각보다 너무 미래라 장비 시계를 믿을 수 없음 */
    CLOCK_SKEW
}
