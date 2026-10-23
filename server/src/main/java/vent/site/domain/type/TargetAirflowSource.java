package vent.site.domain.type;

/**
 * 목표 풍량의 출처.
 *
 * <p>TAB으로 맞춘 값이 있으면 그 값을, 없으면 최소 환기량을 목표 풍량으로 쓴다 (스펙 5.3).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public enum TargetAirflowSource {
    /** TAB으로 측정하고 맞춘 값 */
    TAB,
    /** 거주 인원과 바닥면적으로 계산한 최소 환기량 */
    MINIMUM_AIRFLOW
}
