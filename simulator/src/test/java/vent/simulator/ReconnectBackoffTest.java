package vent.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 재접속 간격(지수 백오프 + jitter) 단위 테스트.
 *
 * <p>테스트 지점: 다음 대기 시간을 돌려주는 공개 메서드. 무작위 값을 바깥에서 넣어 가장 짧은 경우(0)와 가장 긴 경우(1에 가까움)를
 * 직접 적은 숫자로 확인한다. 기준: 1초에서 시작해 두 배씩, 최대 60초, ±30% 흔들기(#3).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
class ReconnectBackoffTest {

    @Test
    @DisplayName("첫 재시도는 1초의 ±30% 안이다")
    void firstDelayIsAroundOneSecond() {
        assertThat(new ReconnectBackoff(() -> 0.0).nextDelay()).isEqualTo(Duration.ofMillis(700));
        assertThat(new ReconnectBackoff(() -> 0.999_999).nextDelay()).isBetween(Duration.ofMillis(1_299), Duration.ofMillis(1_300));
    }

    @Test
    @DisplayName("재시도할 때마다 기준 간격이 두 배가 된다: 1초, 2초, 4초, 8초")
    void delayDoublesEachAttempt() {
        ReconnectBackoff backoff = new ReconnectBackoff(() -> 0.5);

        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    @DisplayName("기준 간격은 60초를 넘지 않는다")
    void delayIsCappedAtSixtySeconds() {
        ReconnectBackoff backoff = new ReconnectBackoff(() -> 0.5);
        for (int i = 0; i < 10; i++) {
            backoff.nextDelay();
        }

        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("연결에 성공해 초기화하면 다시 1초부터 시작한다")
    void resetStartsOverFromOneSecond() {
        ReconnectBackoff backoff = new ReconnectBackoff(() -> 0.5);
        backoff.nextDelay();
        backoff.nextDelay();

        backoff.reset();

        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(1));
    }
}
