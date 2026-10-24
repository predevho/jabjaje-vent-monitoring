package vent.simulator;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * 재접속 대기 시간 계산기 (지수 백오프 + jitter).
 *
 * <p>실패할 때마다 기준 간격을 1초에서 두 배씩 늘리고(기준 최대 60초), 매번 ±30% 무작위로 흔든다.
 * 그래서 실제 대기는 0.7초~78초 사이다. 최댓값에서도 흔들어야 동시에 재접속하는 게이트웨이들이 흩어진다.
 * 브로커가 재시작될 때 모든 게이트웨이가 같은 순간에 몰려 접속하는 것을 막기 위해서다(스펙 4.3, 9.1 C9).
 * 연결에 성공하면 {@link #reset()}으로 처음부터 다시 센다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
public class ReconnectBackoff {

    private static final long INITIAL_MILLIS = 1_000;
    private static final long MAX_MILLIS = 60_000;
    private static final double JITTER_RATIO = 0.3;
    /** 2^6 = 64초에서 이미 최댓값을 넘으므로 그 이상은 계산하지 않는다(오버플로 방지). */
    private static final int MAX_EXPONENT = 6;

    private final DoubleSupplier random;
    private int attempt;

    /** 실제 무작위 값을 쓴다. */
    public ReconnectBackoff() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    /** @param random 0 이상 1 미만 값을 주는 공급자. 테스트에서 고정값을 넣는다 */
    public ReconnectBackoff(DoubleSupplier random) {
        this.random = random;
    }

    /** 다음에 기다릴 시간을 돌려주고, 재시도 횟수를 하나 늘린다. */
    public Duration nextDelay() {
        long base = Math.min(INITIAL_MILLIS << Math.min(attempt, MAX_EXPONENT), MAX_MILLIS);
        attempt++;
        double factor = 1 - JITTER_RATIO + 2 * JITTER_RATIO * random.getAsDouble();
        return Duration.ofMillis(Math.round(base * factor));
    }

    /** 연결에 성공하면 부른다. 다음 실패는 다시 1초부터 센다. */
    public void reset() {
        attempt = 0;
    }
}
