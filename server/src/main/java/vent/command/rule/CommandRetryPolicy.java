package vent.command.rule;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * 원격 명령 재시도·시간 초과 판단 (스펙 7.1 7단계, #9).
 *
 * <p>응답을 기다리는 명령(보냄) 하나에 대해, 마지막으로 보낸 시각(첫 발행 또는 재시도)부터 기다린 시간으로 판단한다.
 * <ul>
 *   <li>10초가 안 지났으면 기다린다</li>
 *   <li>지났고 재시도 횟수가 남았으면 같은 commandId로 재시도한다</li>
 *   <li>지났고 재시도를 다 썼으면 시간 초과</li>
 * </ul>
 * 이미 만료된 명령은 다시 보내도 장비가 버리므로 재시도하지 않고 바로 시간 초과다(서버가 오래 멈췄다 깨어난 경우).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
@Component
public class CommandRetryPolicy {

    private final RemoteCommandReliabilityProperties properties;

    public CommandRetryPolicy(RemoteCommandReliabilityProperties properties) {
        this.properties = properties;
    }

    /**
     * @param input 재시도 횟수, 보낸 시각, 재시도 시각, 만료 시각
     * @param now   판단 시각
     */
    public CommandRetryAction decide(CommandRetryInput input, OffsetDateTime now) {
        OffsetDateTime lastSentAt = input.retriedAt() != null ? input.retriedAt() : input.sentAt();
        if (!now.isAfter(lastSentAt.plus(properties.responseWait()))) {
            return CommandRetryAction.WAIT;
        }
        boolean expired = input.expiresAt() != null && !now.isBefore(input.expiresAt());
        return input.retryCount() < properties.maxRetries() && !expired ? CommandRetryAction.RETRY : CommandRetryAction.TIME_OUT;
    }
}
