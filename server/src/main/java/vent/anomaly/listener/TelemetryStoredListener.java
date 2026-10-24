package vent.anomaly.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vent.anomaly.service.AnomalyEvaluationService;
import vent.telemetry.service.TelemetryStoredEvent;

/**
 * 측정값 저장 완료 이벤트 수신 처리기.
 *
 * <p>측정값 저장이 커밋된 뒤에 그 장비의 이상 규칙을 평가한다(ADR-0013). 이상 평가에서 오류가 나도 측정값 저장은
 * 이미 끝났으므로 되돌리지 않는다. 오류는 로그로 남기고, 다음 측정값이 들어올 때 다시 평가된다.
 * 다른 기능에서 오는 이벤트를 받는 입구라 listener 레이어에 둔다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@Component
public class TelemetryStoredListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryStoredListener.class);

    private final AnomalyEvaluationService evaluationService;

    public TelemetryStoredListener(AnomalyEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetryStored(TelemetryStoredEvent event) {
        try {
            evaluationService.evaluateDevice(event.deviceId());
        } catch (RuntimeException e) {
            // 여기서 예외가 나가면 이미 커밋된 측정값 처리까지 실패한 것처럼 보여 메시지를 다시 받게 된다.
            log.error("이상 평가 실패, 다음 측정값에서 다시 평가: {} ({})", event.deviceId(), event.messageId(), e);
        }
    }
}
