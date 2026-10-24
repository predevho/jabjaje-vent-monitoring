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
    /** CO2 과다: 1,000ppm 초과가 10분 지속 (#5) */
    CO2_HIGH,
    /** 목표 풍량 미달: 급기가 목표 풍량의 80% 미만으로 30분 지속 (#5) */
    AIRFLOW_BELOW_TARGET,
    /** 바이패스 지속: 바이패스가 60분 넘게 지속 (#5) */
    BYPASS_TOO_LONG,
    /** 게이트웨이 오프라인: 연결이 끊긴 상태가 오래(기본 30분) 이어짐. 이상은 게이트웨이에 붙는다 (#6) */
    GATEWAY_OFFLINE,
    /** VEMC 무반응: 후드를 켠 뒤 60초가 지나도 같은 현장 환기장치의 급기가 오르지 않음. 이상은 VEMC에 붙는다 (#7) */
    VEMC_NO_RESPONSE
}
