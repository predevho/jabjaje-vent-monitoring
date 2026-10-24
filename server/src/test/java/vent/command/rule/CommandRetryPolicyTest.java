package vent.command.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 원격 명령 재시도·시간 초과 판단 단위 테스트.
 *
 * <p>테스트 지점: 판단 클래스의 공개 메서드. 기준은 스펙 7.1(10초 동안 응답이 없으면 같은 commandId로 1회 재시도,
 * 그래도 없으면 시간 초과, 만료는 발행 + 60초)이고 시각은 직접 넣는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
class CommandRetryPolicyTest {

    private static final OffsetDateTime SENT_AT = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);

    private final CommandRetryPolicy policy = new CommandRetryPolicy(new RemoteCommandReliabilityProperties(
            Duration.ofSeconds(10), 1, Duration.ofSeconds(60)));

    @Test
    @DisplayName("보낸 지 10초 정각까지는 기다리고, 넘으면 재시도한다")
    void retriesAfterResponseWait() {
        assertThat(decide(0, null, SENT_AT.plusSeconds(10))).isEqualTo(CommandRetryAction.WAIT);
        assertThat(decide(0, null, SENT_AT.plusSeconds(11))).isEqualTo(CommandRetryAction.RETRY);
    }

    @Test
    @DisplayName("재시도한 뒤 10초 정각까지는 기다리고, 넘으면 시간 초과")
    void timesOutAfterRetryWait() {
        OffsetDateTime retriedAt = SENT_AT.plusSeconds(11);
        assertThat(decide(1, retriedAt, retriedAt.plusSeconds(10))).isEqualTo(CommandRetryAction.WAIT);
        assertThat(decide(1, retriedAt, retriedAt.plusSeconds(11))).isEqualTo(CommandRetryAction.TIME_OUT);
    }

    @Test
    @DisplayName("서버가 오래 멈췄다 깨어나 이미 만료된 명령은 다시 보내지 않고 바로 시간 초과")
    void expiredCommandTimesOutWithoutRetry() {
        assertThat(decide(0, null, SENT_AT.plusMinutes(10))).isEqualTo(CommandRetryAction.TIME_OUT);
    }

    private CommandRetryAction decide(int retryCount, OffsetDateTime retriedAt, OffsetDateTime now) {
        return policy.decide(new CommandRetryInput(retryCount, SENT_AT, retriedAt, SENT_AT.plusSeconds(60)), now);
    }
}
