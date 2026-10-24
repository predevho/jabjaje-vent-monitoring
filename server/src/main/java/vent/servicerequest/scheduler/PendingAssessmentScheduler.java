package vent.servicerequest.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vent.servicerequest.service.AssessmentService;

/**
 * 접수된 채 남은 A/S 신청 재처리 스케줄러 (#10).
 *
 * <p>주기마다(기본 60초) 접수된 지 오래됐는데 소견이 없는 신청을 다시 처리한다. 로직은 없고 서비스만 부른다.
 * {@code vent.assessment.pending-check-enabled=false}면 만들지 않는다(통합 테스트는 끄고 직접 부른다).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Component
@ConditionalOnProperty(prefix = "vent.assessment", name = "pending-check-enabled", havingValue = "true", matchIfMissing = true)
public class PendingAssessmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingAssessmentScheduler.class);

    private final AssessmentService assessmentService;

    public PendingAssessmentScheduler(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @Scheduled(fixedDelayString = "${vent.assessment.pending-check-interval:60s}")
    public void retryPending() {
        try {
            assessmentService.retryPending(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            log.error("접수된 A/S 신청 재처리 실패, 다음 주기에 다시 확인", e);
        }
    }
}
