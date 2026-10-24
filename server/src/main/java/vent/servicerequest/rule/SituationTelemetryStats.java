package vent.servicerequest.rule;

/**
 * 기간 안의 측정값 집계 (#10). DB가 계산해 돌려준다.
 *
 * @param count          품질 OK 측정값 수 (늦은 측정값 포함)
 * @param co2Avg         CO2 평균. 측정값이 없으면 null
 * @param co2Max         CO2 최대
 * @param supplyAvg      바이패스·부스트가 아닐 때의 급기 평균
 * @param bypassCount    바이패스였던 측정값 수
 * @param filterStartAvg 기간 첫 구간의 필터 차압 평균
 * @param filterLatest   최근 필터 차압
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationTelemetryStats(long count, Double co2Avg, Integer co2Max, Double supplyAvg, long bypassCount,
                                      Double filterStartAvg, Integer filterLatest) {

    /** 측정값이 없을 때 */
    public static final SituationTelemetryStats NONE = new SituationTelemetryStats(0, null, null, null, 0, null, null);
}
