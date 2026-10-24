package vent.servicerequest.rule;

import java.time.Duration;
import java.util.List;

/**
 * 상황 요약의 재료가 되는 DB 집계 결과 (#10). 계산은 {@link SituationSummaryCalculator}가 한다.
 *
 * @param site              현장 정보 (이름 없음)
 * @param window            요약 기간 (7일)
 * @param telemetryInterval 측정 주기 (30초). 받았어야 할 측정값 수와 바이패스 시간을 셀 때 쓴다
 * @param telemetry         측정값 집계
 * @param hood              후드 연동 확인 결과 수
 * @param offlineCount      게이트웨이 오프라인 이상 수 (지금 열린 것과 기간 안에 생긴 것)
 * @param openAnomalies     지금 열린 이상
 * @param recentAnomalies   기간 안에 생겼다가 닫힌 이상
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationStats(SituationSiteProfile site, Duration window, Duration telemetryInterval,
                             SituationTelemetryStats telemetry, SituationHoodStats hood, long offlineCount,
                             List<SituationAnomaly> openAnomalies, List<SituationAnomaly> recentAnomalies) {
}
