package vent.anomaly.domain.type;

/**
 * 이상 심각도.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public enum AnomalySeverity {
    /** 정보: 알아 두면 좋은 것 */
    INFO,
    /** 주의: 점검이 필요한 것 */
    WARN,
    /** 심각: 바로 조치가 필요한 것 */
    CRITICAL
}
