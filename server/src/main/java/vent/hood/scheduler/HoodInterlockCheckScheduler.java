package vent.hood.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vent.hood.service.HoodInterlockCheckService;

/**
 * 후드 연동 확인 스케줄러.
 *
 * <p>주기마다(기본 30초) 확인 전 HOOD_ON을 확인한다(스펙 6.2). 로직은 없고 서비스만 부른다.
 * 주기는 {@code vent.hood.interlock.check-interval}이고, {@code vent.hood.interlock.check-enabled=false}면 만들지 않는다
 * (통합 테스트는 끄고 확인을 직접 부른다).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Component
@ConditionalOnProperty(prefix = "vent.hood.interlock", name = "check-enabled", havingValue = "true", matchIfMissing = true)
public class HoodInterlockCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoodInterlockCheckScheduler.class);

    private final HoodInterlockCheckService checkService;

    public HoodInterlockCheckScheduler(HoodInterlockCheckService checkService) {
        this.checkService = checkService;
    }

    @Scheduled(fixedDelayString = "${vent.hood.interlock.check-interval:30s}")
    public void checkPending() {
        try {
            checkService.checkPending(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            log.error("후드 연동 확인 실패, 다음 주기에 다시 확인", e);
        }
    }
}
