package vent.servicerequest.listener;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vent.servicerequest.service.AssessmentService;
import vent.servicerequest.service.ServiceRequestReceivedEvent;

/**
 * A/S 신청 접수 이벤트 수신 처리기 (#10).
 *
 * <p>접수 트랜잭션이 커밋된 뒤 AI 소견 전용 작업 스레드에서 소견을 만든다. 고객 응답은 AI를 기다리지 않는다(스펙 8.1).
 * 실패하거나 작업 대기열이 가득 차 버려지면 신청은 접수됨으로 남고, 재처리 스케줄러가 다시 맡는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Component
public class ServiceRequestReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestReceivedListener.class);

    private final AssessmentService assessmentService;

    public ServiceRequestReceivedListener(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @Async("assessmentExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReceived(ServiceRequestReceivedEvent event) {
        try {
            assessmentService.assess(event.serviceRequestId(), OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            log.error("A/S 신청 {} AI 소견 실패, 재처리 스케줄러가 다시 시도", event.serviceRequestId(), e);
        }
    }
}
