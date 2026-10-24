package vent.hood.rule;

import java.time.OffsetDateTime;

/**
 * 후드 연동 확인에 쓰는 급기 측정값 하나.
 *
 * @param measuredAt 측정 시각 (ADR-0003)
 * @param supplyCmh  급기량 (CMH)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public record HoodInterlockReading(OffsetDateTime measuredAt, int supplyCmh) {
}
