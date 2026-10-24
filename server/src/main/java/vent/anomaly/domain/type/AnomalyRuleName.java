package vent.anomaly.domain.type;

/**
 * 이상 규칙 이름 (스펙 6.1).
 *
 * <p>규칙이 늘면 여기에 추가한다. DB에는 이 이름이 문자열로 저장된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public enum AnomalyRuleName {
    /** 필터 막힘: 필터 차압이 모델별 기준을 넘음 */
    FILTER_CLOGGED,
    /** 게이트웨이 오프라인: 연결이 끊긴 상태가 오래(기본 30분) 이어짐. 이상은 게이트웨이에 붙는다 (#6) */
    GATEWAY_OFFLINE
}
