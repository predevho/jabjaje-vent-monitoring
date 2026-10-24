package vent.simulator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 장비의 원격 명령 처리 (스펙 4.3, 7.2, 7.3, #8, #9).
 *
 * <p>한 번에 도착한 명령 묶음을 아래 순서로 처리하고 응답 목록을 돌려준다. 게이트웨이 스레드에서만 쓴다.
 * <ol>
 *   <li>이미 처리한 commandId(재시도, 중복 전달)는 다시 적용하지 않고 이전 결과를 다시 응답한다</li>
 *   <li>만료 시각이 지난 명령은 {@code EXPIRED}로 거절한다</li>
 *   <li>남은 명령 중 issuedAt이 가장 최신인 것 하나만 본다. 나머지는 {@code SUPERSEDED}로 거절한다</li>
 *   <li>그 명령이 마지막으로 적용한 명령보다 먼저 요청된 것이면 {@code SUPERSEDED}</li>
 *   <li>장비 우선순위(자기 보호 → 후드 연동)에 걸리면 그 사유로 거절, 아니면 적용</li>
 * </ol>
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
final class RemoteCommandHandler {

    /** 이미 처리한 commandId를 이만큼 기억한다 */
    private static final int REMEMBERED_COMMANDS = 100;

    /**
     * 부스트를 받는 장비.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#9)
     */
    interface BoostDevice {
        /** 지금 부스트를 거절해야 하는 이유. 적용할 수 있으면 빈 값 */
        Optional<String> boostRejectionReason();

        void startBoost(int supplyCmh, int exhaustCmh, Duration duration, OffsetDateTime now);
    }

    /**
     * 받은 원격 명령 하나.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#9)
     */
    record Command(String commandId, String type, OffsetDateTime issuedAt, OffsetDateTime expiresAt,
                   int supplyCmh, int exhaustCmh, int durationSec) {
    }

    /**
     * 보낼 응답 하나. 적용이면 reason이 없다.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#9)
     */
    record Ack(String commandId, String result, String reason) {

        static Ack applied(String commandId) {
            return new Ack(commandId, "APPLIED", null);
        }

        static Ack rejected(String commandId, String reason) {
            return new Ack(commandId, "REJECTED", reason);
        }
    }

    private final Map<String, Ack> processed = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Ack> eldest) {
            return size() > REMEMBERED_COMMANDS;
        }
    };
    private OffsetDateTime lastAppliedIssuedAt;

    List<Ack> handle(List<Command> batch, OffsetDateTime now, BoostDevice device) {
        List<Ack> acks = new ArrayList<>();
        Map<String, Command> fresh = new LinkedHashMap<>();
        for (Command command : batch) {
            Ack previous = processed.get(command.commandId());
            if (previous != null) {
                acks.add(previous);
            } else {
                fresh.putIfAbsent(command.commandId(), command);
            }
        }

        List<Command> candidates = new ArrayList<>();
        for (Command command : fresh.values()) {
            if (!"BOOST".equals(command.type())) {
                acks.add(remember(Ack.rejected(command.commandId(), "UNSUPPORTED")));
            } else if (command.issuedAt() == null) {
                // 최신을 가를 수 없는 명령은 적용하지 않는다.
                acks.add(remember(Ack.rejected(command.commandId(), "INVALID")));
            } else if (command.expiresAt() != null && now.isAfter(command.expiresAt())) {
                acks.add(remember(Ack.rejected(command.commandId(), "EXPIRED")));
            } else {
                candidates.add(command);
            }
        }
        if (candidates.isEmpty()) {
            return acks;
        }

        Command latest = candidates.stream().max(Comparator.comparing(Command::issuedAt)).orElseThrow();
        for (Command older : candidates) {
            if (older != latest) {
                acks.add(remember(Ack.rejected(older.commandId(), "SUPERSEDED")));
            }
        }
        acks.add(remember(decide(latest, now, device)));
        return acks;
    }

    private Ack decide(Command latest, OffsetDateTime now, BoostDevice device) {
        if (lastAppliedIssuedAt != null && !latest.issuedAt().isAfter(lastAppliedIssuedAt)) {
            return Ack.rejected(latest.commandId(), "SUPERSEDED");
        }
        Optional<String> rejection = device.boostRejectionReason();
        if (rejection.isPresent()) {
            return Ack.rejected(latest.commandId(), rejection.get());
        }
        device.startBoost(latest.supplyCmh(), latest.exhaustCmh(), Duration.ofSeconds(latest.durationSec()), now);
        lastAppliedIssuedAt = latest.issuedAt();
        return Ack.applied(latest.commandId());
    }

    private Ack remember(Ack ack) {
        processed.put(ack.commandId(), ack);
        return ack;
    }
}
