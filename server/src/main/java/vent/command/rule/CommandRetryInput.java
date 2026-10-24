package vent.command.rule;

import java.time.OffsetDateTime;

/**
 * 원격 명령 재시도·시간 초과 판단 재료 (#9).
 *
 * @param retryCount 지금까지 재시도한 횟수
 * @param sentAt     처음 보낸 시각
 * @param retriedAt  마지막으로 재시도한 시각. 재시도 전이면 null
 * @param expiresAt  만료 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
public record CommandRetryInput(int retryCount, OffsetDateTime sentAt, OffsetDateTime retriedAt, OffsetDateTime expiresAt) {
}
