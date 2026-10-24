package vent.servicerequest.rule;

/**
 * 필터 차압 추세 (#10). 기간 첫 구간의 평균에서 최근 값으로 얼마나 올랐는지.
 *
 * @param startPa  기간 첫 구간(7일이면 첫날) 평균 (Pa)
 * @param latestPa 최근 값 (Pa)
 * @param changePa 변화량 (Pa). 양수면 막히는 중
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationFilterPaTrend(int startPa, int latestPa, int changePa) {
}
