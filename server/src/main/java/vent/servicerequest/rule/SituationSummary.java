package vent.servicerequest.rule;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 상황 요약 (스펙 8.2, #10). AI에 원본 측정값 대신 보내는 통계다. 숫자 계산은 코드가 하고 AI는 해석만 한다(ADR-0005).
 *
 * <p>구성 요소 이름이 곧 지표 이름이다. AI 소견의 근거({@code evidence})는 {@link #availableMetricKeys()}에 있는
 * 이름만 쓸 수 있다. 값이 null이거나 목록이 비면 근거가 될 데이터가 없다는 뜻이다.
 *
 * @param site                     현장 정보 (이름 없음)
 * @param co2AvgPpm                최근 7일 CO2 평균
 * @param co2MaxPpm                최근 7일 CO2 최대
 * @param supplyToTargetRatio      급기 평균 ÷ 목표 풍량 (바이패스·부스트 제외, 소수 둘째 자리)
 * @param filterPaTrend            필터 차압 추세
 * @param bypassHours              바이패스 누적 시간 (시간, 소수 첫째 자리)
 * @param bypassShare              바이패스였던 측정값 비율 (%, 소수 첫째 자리)
 * @param hoodInterlockSuccessRate 후드 연동 확인 정상 ÷ (정상 + 무반응) (소수 둘째 자리)
 * @param offlineCount             게이트웨이 오프라인 이상 수
 * @param dataCoverage             받은 측정값 수 ÷ 받았어야 할 수 (0~1, 소수 둘째 자리). 낮으면 판단 보류 근거
 * @param openAnomalies            지금 열린 이상
 * @param recentAnomalies          최근 7일에 생겼다가 닫힌 이상
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationSummary(SituationSiteProfile site, Integer co2AvgPpm, Integer co2MaxPpm, Double supplyToTargetRatio,
                               SituationFilterPaTrend filterPaTrend, Double bypassHours, Double bypassShare,
                               Double hoodInterlockSuccessRate, int offlineCount, double dataCoverage,
                               List<SituationAnomaly> openAnomalies, List<SituationAnomaly> recentAnomalies) {

    /** 값이 있어 AI 소견의 근거로 쓸 수 있는 지표 이름. 데이터가 없는 지표를 근거로 대면 검증에서 걸린다 */
    public Set<String> availableMetricKeys() {
        Set<String> keys = new LinkedHashSet<>();
        addIf(keys, "co2AvgPpm", co2AvgPpm != null);
        addIf(keys, "co2MaxPpm", co2MaxPpm != null);
        addIf(keys, "supplyToTargetRatio", supplyToTargetRatio != null);
        addIf(keys, "filterPaTrend", filterPaTrend != null);
        addIf(keys, "bypassHours", bypassHours != null);
        addIf(keys, "bypassShare", bypassShare != null);
        addIf(keys, "hoodInterlockSuccessRate", hoodInterlockSuccessRate != null);
        keys.add("offlineCount");
        keys.add("dataCoverage");
        addIf(keys, "openAnomalies", !openAnomalies.isEmpty());
        addIf(keys, "recentAnomalies", !recentAnomalies.isEmpty());
        return keys;
    }

    private static void addIf(Set<String> keys, String key, boolean present) {
        if (present) {
            keys.add(key);
        }
    }
}
