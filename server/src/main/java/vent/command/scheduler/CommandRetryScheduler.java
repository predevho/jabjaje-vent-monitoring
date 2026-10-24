package vent.command.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vent.command.service.CommandRetryService;

/**
 * 원격 명령 재시도·시간 초과 스케줄러.
 *
 * <p>주기마다(기본 2초) 응답을 기다리는 명령을 확인한다(스펙 7.1). 로직은 없고 서비스만 부른다.
 * 주기는 {@code vent.command.reliability.check-interval}이고, {@code vent.command.reliability.check-enabled=false}면
 * 만들지 않는다(통합 테스트는 끄고 확인을 직접 부른다).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
@Component
@ConditionalOnProperty(prefix = "vent.command.reliability", name = "check-enabled", havingValue = "true", matchIfMissing = true)
public class CommandRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommandRetryScheduler.class);

    private final CommandRetryService retryService;

    public CommandRetryScheduler(CommandRetryService retryService) {
        this.retryService = retryService;
    }

    @Scheduled(fixedDelayString = "${vent.command.reliability.check-interval:2s}")
    public void checkPending() {
        try {
            retryService.checkPending(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            log.error("원격 명령 재시도 확인 실패, 다음 주기에 다시 확인", e);
        }
    }
}
