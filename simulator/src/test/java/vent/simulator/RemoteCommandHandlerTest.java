package vent.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.simulator.RemoteCommandHandler.Ack;
import vent.simulator.RemoteCommandHandler.Command;

/**
 * 장비의 원격 명령 처리 단위 테스트.
 *
 * <p>테스트 지점: 명령 처리 클래스의 공개 메서드. 기준은 스펙 7.2의 장비 규칙(만료된 명령은 EXPIRED, 이미 처리한 commandId는
 * 다시 적용하지 않음, 몰려온 명령 중 issuedAt이 가장 최신인 것 하나만 적용하고 나머지는 SUPERSEDED)이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
class RemoteCommandHandlerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);

    private final RemoteCommandHandler handler = new RemoteCommandHandler();
    private final FakeDevice device = new FakeDevice();

    @Test
    @DisplayName("만료된 명령은 적용하지 않고 EXPIRED로 거절한다")
    void expiredCommandIsRejected() {
        List<Ack> acks = handler.handle(List.of(boost("c1", NOW.minusSeconds(70), NOW.minusSeconds(10), 260)), NOW, device);

        assertThat(acks).containsExactly(new Ack("c1", "REJECTED", "EXPIRED"));
        assertThat(device.boosts).isEmpty();
    }

    @Test
    @DisplayName("이미 처리한 commandId가 다시 오면(재시도) 다시 적용하지 않고 이전 결과만 다시 응답한다")
    void duplicateCommandRepeatsPreviousResult() {
        Command command = boost("c1", NOW.minusSeconds(5), NOW.plusSeconds(55), 260);
        assertThat(handler.handle(List.of(command), NOW, device)).containsExactly(new Ack("c1", "APPLIED", null));

        assertThat(handler.handle(List.of(command), NOW.plusSeconds(10), device)).containsExactly(new Ack("c1", "APPLIED", null));
        assertThat(device.boosts).hasSize(1);
    }

    @Test
    @DisplayName("몰려온 명령 중 issuedAt이 가장 최신인 것 하나만 적용하고, 나머지는 SUPERSEDED. 같은 묶음 안의 중복은 한 번만 응답")
    void burstAppliesOnlyLatest() {
        Command first = boost("c1", NOW.minusSeconds(30), NOW.plusSeconds(30), 230);
        Command second = boost("c2", NOW.minusSeconds(20), NOW.plusSeconds(40), 250);
        Command latest = boost("c3", NOW.minusSeconds(10), NOW.plusSeconds(50), 270);

        List<Ack> acks = handler.handle(List.of(latest, first, latest, second), NOW, device);

        assertThat(acks).containsExactlyInAnyOrder(
                new Ack("c1", "REJECTED", "SUPERSEDED"),
                new Ack("c2", "REJECTED", "SUPERSEDED"),
                new Ack("c3", "APPLIED", null));
        assertThat(device.boosts).containsExactly(270);
    }

    @Test
    @DisplayName("마지막으로 적용한 명령보다 먼저 요청된 명령이 나중에 오면 SUPERSEDED")
    void olderThanLastAppliedIsSuperseded() {
        handler.handle(List.of(boost("c2", NOW.minusSeconds(10), NOW.plusSeconds(50), 260)), NOW, device);

        List<Ack> acks = handler.handle(List.of(boost("c1", NOW.minusSeconds(20), NOW.plusSeconds(40), 230)), NOW, device);

        assertThat(acks).containsExactly(new Ack("c1", "REJECTED", "SUPERSEDED"));
        assertThat(device.boosts).containsExactly(260);
    }

    @Test
    @DisplayName("자기 보호 중이면 최신 명령도 그 사유로 거절하고, 다음 명령은 다시 적용할 수 있다")
    void deviceProtectionRejectsLatest() {
        device.rejection = Optional.of("FROST_PROTECTION");
        assertThat(handler.handle(List.of(boost("c1", NOW.minusSeconds(5), NOW.plusSeconds(55), 260)), NOW, device))
                .containsExactly(new Ack("c1", "REJECTED", "FROST_PROTECTION"));

        device.rejection = Optional.empty();
        assertThat(handler.handle(List.of(boost("c2", NOW.minusSeconds(1), NOW.plusSeconds(59), 260)), NOW, device))
                .containsExactly(new Ack("c2", "APPLIED", null));
    }

    @Test
    @DisplayName("요청 시각이 없는 명령은 최신을 가를 수 없어 INVALID로 거절한다")
    void missingIssuedAtIsInvalid() {
        assertThat(handler.handle(List.of(new Command("c1", "BOOST", null, NOW.plusSeconds(50), 260, 260, 600)), NOW, device))
                .containsExactly(new Ack("c1", "REJECTED", "INVALID"));
        assertThat(device.boosts).isEmpty();
    }

    private static Command boost(String commandId, OffsetDateTime issuedAt, OffsetDateTime expiresAt, int supplyCmh) {
        return new Command(commandId, "BOOST", issuedAt, expiresAt, supplyCmh, supplyCmh, 600);
    }

    /**
     * 테스트용 장비. 적용한 부스트 급기를 모아 두고, 거절 사유를 바꿀 수 있다.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#9)
     */
    private static final class FakeDevice implements RemoteCommandHandler.BoostDevice {

        private final List<Integer> boosts = new ArrayList<>();
        private Optional<String> rejection = Optional.empty();

        @Override
        public Optional<String> boostRejectionReason() {
            return rejection;
        }

        @Override
        public void startBoost(int supplyCmh, int exhaustCmh, Duration duration, OffsetDateTime now) {
            boosts.add(supplyCmh);
        }
    }
}
