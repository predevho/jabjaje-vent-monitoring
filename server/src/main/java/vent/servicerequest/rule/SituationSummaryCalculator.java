package vent.servicerequest.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 상황 요약 계산 (스펙 8.2, #10).
 *
 * <p>DB 집계 결과를 AI에 보낼 지표로 바꾼다. 비율은 반올림하고, 분모가 0이라 계산할 수 없는 지표는 비워 둔다.
 * 비워 둔 지표와 낮은 데이터 충분도는 AI가 판단을 보류하는 근거가 된다(스펙 9.5 A4).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Component
public class SituationSummaryCalculator {

    public SituationSummary summarize(SituationStats stats) {
        SituationTelemetryStats t = stats.telemetry();
        long intervalSec = stats.telemetryInterval().toSeconds();
        long expected = stats.window().toSeconds() / intervalSec;
        double coverage = expected == 0 ? 0 : Math.min(1.0, (double) t.count() / expected);

        Double ratio = t.supplyAvg() == null || stats.site().targetAirflowCmh() <= 0 ? null
                : round(t.supplyAvg() / stats.site().targetAirflowCmh(), 2);
        SituationFilterPaTrend filterTrend = t.filterStartAvg() == null || t.filterLatest() == null ? null
                : trend((int) Math.round(t.filterStartAvg()), t.filterLatest());
        Double bypassHours = t.count() == 0 ? null : round(t.bypassCount() * intervalSec / 3600.0, 1);
        Double bypassShare = t.count() == 0 ? null : round(t.bypassCount() * 100.0 / t.count(), 1);
        long hoodChecked = stats.hood().confirmed() + stats.hood().noResponse();
        Double hoodRate = hoodChecked == 0 ? null : round((double) stats.hood().confirmed() / hoodChecked, 2);

        return new SituationSummary(
                stats.site(),
                t.co2Avg() == null ? null : (int) Math.round(t.co2Avg()),
                t.co2Max(),
                ratio,
                filterTrend,
                bypassHours,
                bypassShare,
                hoodRate,
                (int) stats.offlineCount(),
                round(coverage, 2),
                stats.openAnomalies(),
                stats.recentAnomalies());
    }

    private static SituationFilterPaTrend trend(int startPa, int latestPa) {
        return new SituationFilterPaTrend(startPa, latestPa, latestPa - startPa);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
