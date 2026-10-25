package vent.anomaly.rule;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * "조건이 N분 동안 계속" 판단 도우미.
 *
 * <p>가장 최신 측정값부터 거꾸로 가며, 조건을 만족하는 측정값이 끊기지 않고 이어진 구간을 찾는다(#5).
 * 조건을 만족하지 않는 측정값을 만나거나, 바로 앞 측정값과의 간격이 최대 간격보다 길면 거기서 끊는다.
 * 측정값이 빈 구간(끊긴 동안 늦은 측정값을 뺀 자리)을 하나로 이어 붙이면 몇 시간 전 값으로 지금 이상이 생기기 때문이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
final class AnomalyRuleStreak {

    private AnomalyRuleStreak() {
    }

    /**
     * 최신 측정값부터 조건이 이어진 시간.
     *
     * @param newestFirst 측정값(최신 먼저). 늦은 측정값은 미리 뺀다
     * @param condition   조건
     * @param maxGap      이 간격보다 길게 비면 끊긴 것으로 본다
     * @return 최신 측정값이 조건을 만족하면 구간의 첫 측정값부터 최신 측정값까지의 시간, 아니면 빈 값
     */
    static Optional<Duration> lastingFor(List<AnomalyRuleTelemetry> newestFirst,
                                         Predicate<AnomalyRuleTelemetry> condition,
                                         Duration maxGap) {
        if (newestFirst.isEmpty() || !condition.test(newestFirst.get(0))) {
            return Optional.empty();
        }
        AnomalyRuleTelemetry newest = newestFirst.get(0);
        AnomalyRuleTelemetry earliest = newest;
        for (AnomalyRuleTelemetry older : newestFirst.subList(1, newestFirst.size())) {
            boolean gapTooLong = Duration.between(older.measuredAt(), earliest.measuredAt()).compareTo(maxGap) > 0;
            if (gapTooLong || !condition.test(older)) {
                break;
            }
            earliest = older;
        }
        return Optional.of(Duration.between(earliest.measuredAt(), newest.measuredAt()));
    }

    /** 조건이 기준 시간보다 길게 이어졌는지. 스펙이 "N분 넘게"라고 쓴 규칙(바이패스 지속)이 쓴다. */
    static boolean lastedLongerThan(List<AnomalyRuleTelemetry> newestFirst, Predicate<AnomalyRuleTelemetry> condition,
                                    Duration maxGap, Duration limit) {
        return lastingFor(newestFirst, condition, maxGap).filter(lasted -> lasted.compareTo(limit) > 0).isPresent();
    }

    /** 화면 설명에 쓸 시간 표현. 1분 이상이면 "N분", 시연용으로 1분보다 짧게 설정했으면 "N초". */
    static String describe(Duration duration) {
        return duration.toMinutes() >= 1 ? duration.toMinutes() + "분" : duration.toSeconds() + "초";
    }

    /** 조건이 기준 시간 이상 이어졌는지. 스펙이 "N분 지속"이라고 쓴 규칙(CO2, 목표 풍량)이 쓴다. */
    static boolean lastedAtLeast(List<AnomalyRuleTelemetry> newestFirst, Predicate<AnomalyRuleTelemetry> condition,
                                 Duration maxGap, Duration required) {
        return lastingFor(newestFirst, condition, maxGap).filter(lasted -> lasted.compareTo(required) >= 0).isPresent();
    }
}
