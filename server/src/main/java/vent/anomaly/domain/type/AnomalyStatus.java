package vent.anomaly.domain.type;

/**
 * 이상 상태.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public enum AnomalyStatus {
    /** 열림: 문제가 계속되고 있음 */
    OPEN,
    /** 해결: 해제 조건이 맞아 닫힘 */
    RESOLVED
}
