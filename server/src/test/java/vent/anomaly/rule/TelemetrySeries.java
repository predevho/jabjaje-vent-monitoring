package vent.anomaly.rule;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 규칙 단위 테스트용 측정값 묶음 만들기.
 *
 * <p>측정값을 오래된 것부터 30초 간격으로 쌓고, 가장 최신 측정값이 기준 시각({@link #T0})이 되도록 시각을 붙인다.
 * 규칙이 받는 순서(측정 시각이 늦은 것부터)로 돌려준다. 21개면 10분, 61개면 30분, 121개면 60분 구간이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
final class TelemetrySeries {

    /** 가장 최신 측정값의 측정 시각 */
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);
    private static final int INTERVAL_SECONDS = 30;

    /** 오래된 것부터 */
    private final List<Entry> oldestFirst = new ArrayList<>();
    private int nextGapSeconds = INTERVAL_SECONDS;

    /** 측정값 하나의 값과, 바로 앞 측정값과의 간격(초) */
    private record Entry(Reading reading, int secondsAfterPrevious) {
    }

    private TelemetrySeries() {
    }

    static TelemetrySeries start() {
        return new TelemetrySeries();
    }

    /** 같은 값의 측정값을 count개 더한다. */
    TelemetrySeries then(int count, Reading reading) {
        for (int i = 0; i < count; i++) {
            oldestFirst.add(new Entry(reading, nextGapSeconds));
            nextGapSeconds = INTERVAL_SECONDS;
        }
        return this;
    }

    /** 다음 측정값을 앞 측정값보다 이 시간(초) 뒤에 둔다. 그 사이는 측정값이 들어오지 않은 구간이다. */
    TelemetrySeries gapSeconds(int seconds) {
        nextGapSeconds = seconds;
        return this;
    }

    /** 규칙이 받는 순서(최신 먼저)로 돌려준다. 가장 최신이 {@link #T0}이다. */
    List<AnomalyRuleTelemetry> build() {
        List<AnomalyRuleTelemetry> newestFirst = new ArrayList<>();
        OffsetDateTime at = T0;
        for (int i = oldestFirst.size() - 1; i >= 0; i--) {
            Entry entry = oldestFirst.get(i);
            Reading r = entry.reading();
            newestFirst.add(new AnomalyRuleTelemetry(at, r.co2Ppm(), r.supplyCmh(), 85, r.bypass(), r.late()));
            at = at.minusSeconds(entry.secondsAfterPrevious());
        }
        return newestFirst;
    }

    static Reading co2(int ppm) {
        return new Reading(ppm, 200, false, false);
    }

    static Reading supply(int cmh) {
        return new Reading(800, cmh, false, false);
    }

    static Reading bypass(boolean on) {
        return new Reading(800, 200, on, false);
    }

    /**
     * 측정값 하나의 값.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#5)
     */
    record Reading(int co2Ppm, int supplyCmh, boolean bypass, boolean late) {

        /** 같은 값의 늦은 측정값 */
        Reading asLate() {
            return new Reading(co2Ppm, supplyCmh, bypass, true);
        }

        /** 같은 값에 바이패스만 켠 측정값 */
        Reading withBypass() {
            return new Reading(co2Ppm, supplyCmh, true, late);
        }
    }
}
