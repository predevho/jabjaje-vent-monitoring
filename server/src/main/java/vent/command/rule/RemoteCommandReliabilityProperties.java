package vent.command.rule;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 원격 명령 신뢰성 기준 ({@code vent.command.reliability.*}, 스펙 7.1·7.2, #9).
 *
 * <p>검사 주기({@code check-interval})와 검사를 켤지({@code check-enabled})는 스케줄러가 직접 읽는다.
 *
 * @param responseWait 이 시간 동안 응답이 없으면 재시도하고, 재시도 뒤에도 이만큼 없으면 시간 초과 (기본 10초)
 * @param maxRetries   재시도 횟수 (기본 1)
 * @param expiresAfter 발행 뒤 이 시간이 지나면 명령이 만료된다. 브로커 메시지 만료와 장비 검사에 함께 쓴다 (기본 60초)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
@ConfigurationProperties(prefix = "vent.command.reliability")
public record RemoteCommandReliabilityProperties(
        @DefaultValue("10s") Duration responseWait,
        @DefaultValue("1") int maxRetries,
        @DefaultValue("60s") Duration expiresAfter) {
}
