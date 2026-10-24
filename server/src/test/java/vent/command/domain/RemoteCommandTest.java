package vent.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.command.domain.type.RemoteCommandStatus;

/**
 * 원격 명령 상태 바꾸기 단위 테스트.
 *
 * <p>테스트 지점: 원격 명령 엔티티의 공개 메서드. 기준은 스펙 7.1, 7.2와 ADR-0002(밀린 명령은 응답이 와도 무시,
 * 시간 초과 뒤 온 응답은 실제 결과로 바꾸고 늦은 응답으로 표시)이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
class RemoteCommandTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);
    private static final BoostParams PARAMS = new BoostParams(273, 273, 1800);

    @Test
    @DisplayName("보내면 만료 시각이 생기고, 재시도해도 만료 시각은 그대로다")
    void retryKeepsExpiresAt() {
        RemoteCommand command = RemoteCommand.sentBoost("vent-s1", PARAMS, NOW, NOW.plusSeconds(60));

        command.markRetried(NOW.plusSeconds(11));

        assertThat(command.getRetryCount()).isEqualTo(1);
        assertThat(command.getRetriedAt()).isEqualTo(NOW.plusSeconds(11));
        assertThat(command.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(command.getStatus()).isEqualTo(RemoteCommandStatus.SENT);
    }

    @Test
    @DisplayName("밀려난 명령은 나중에 응답이 와도 밀려남을 유지한다")
    void supersededIgnoresLaterAck() {
        RemoteCommand command = RemoteCommand.sentBoost("vent-s1", PARAMS, NOW, NOW.plusSeconds(60));

        assertThat(command.markSuperseded()).isTrue();
        assertThat(command.markApplied(NOW.plusSeconds(5))).isFalse();

        assertThat(command.getStatus()).isEqualTo(RemoteCommandStatus.SUPERSEDED);
        assertThat(command.isLateAck()).isFalse();
    }

    @Test
    @DisplayName("시간 초과 뒤 적용 응답이 오면 적용됨으로 바꾸고 늦은 응답으로 표시한다")
    void lateAppliedAfterTimeout() {
        RemoteCommand command = RemoteCommand.sentBoost("vent-s1", PARAMS, NOW, NOW.plusSeconds(60));
        command.markTimedOut();

        assertThat(command.markApplied(NOW.plusSeconds(40))).isTrue();

        assertThat(command.getStatus()).isEqualTo(RemoteCommandStatus.APPLIED);
        assertThat(command.isLateAck()).isTrue();
        assertThat(command.getAckedAt()).isEqualTo(NOW.plusSeconds(40));
    }

    @Test
    @DisplayName("시간 초과 뒤 거절 응답이 와도 거절됨으로 바꾸고 늦은 응답으로 표시한다")
    void lateRejectedAfterTimeout() {
        RemoteCommand command = RemoteCommand.sentBoost("vent-s1", PARAMS, NOW, NOW.plusSeconds(60));
        command.markTimedOut();

        assertThat(command.markRejected("FROST_PROTECTION", NOW.plusSeconds(40))).isTrue();

        assertThat(command.getStatus()).isEqualTo(RemoteCommandStatus.REJECTED);
        assertThat(command.getRejectReason()).isEqualTo("FROST_PROTECTION");
        assertThat(command.isLateAck()).isTrue();
    }

    @Test
    @DisplayName("제때 온 응답은 늦은 응답이 아니고, 이미 끝난 명령의 응답(중복)은 무시한다")
    void onTimeAckAndDuplicate() {
        RemoteCommand command = RemoteCommand.sentBoost("vent-s1", PARAMS, NOW, NOW.plusSeconds(60));

        assertThat(command.markApplied(NOW.plusSeconds(2))).isTrue();
        assertThat(command.isLateAck()).isFalse();
        assertThat(command.markRejected("FROST_PROTECTION", NOW.plusSeconds(3))).isFalse();
        assertThat(command.getStatus()).isEqualTo(RemoteCommandStatus.APPLIED);
    }

    @Test
    @DisplayName("응답을 기다리는 명령만 밀려나거나 시간 초과가 된다")
    void onlyPendingCommandsTransition() {
        RemoteCommand applied = RemoteCommand.sentBoost("vent-s1", PARAMS, NOW, NOW.plusSeconds(60));
        applied.markApplied(NOW.plusSeconds(2));

        assertThat(applied.markSuperseded()).isFalse();
        assertThat(applied.markTimedOut()).isFalse();
        assertThat(applied.getStatus()).isEqualTo(RemoteCommandStatus.APPLIED);
    }
}
