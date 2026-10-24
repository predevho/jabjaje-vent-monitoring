package vent.anomaly.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.anomaly.repository.AnomalyRepository;
import vent.command.service.RemoteCommandAnsweredEvent;
import vent.command.service.RemoteCommandTimedOutEvent;

/**
 * 원격 명령 시간 초과 이상 유스케이스 ({@code COMMAND_TIMEOUT}, 스펙 6.1, #9).
 *
 * <p>원격 명령이 재시도 뒤에도 응답이 없으면 그 장비에 정보(INFO) 이상을 열고, 그 장비가 다시 응답하면(다음 명령 정상,
 * 또는 시간 초과된 명령의 늦은 응답) 해제한다. 명령 상태를 바꾼 트랜잭션에 함께 들어가야 해서 {@code MANDATORY}다
 * (ADR-0014와 같은 이유).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
@Service
public class CommandTimeoutAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(CommandTimeoutAnomalyService.class);

    private final AnomalyRepository anomalyRepository;

    public CommandTimeoutAnomalyService(AnomalyRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onTimedOut(RemoteCommandTimedOutEvent event) {
        if (findOpen(event.deviceId()).isPresent()) {
            return;
        }
        String message = "원격 명령에 장비가 응답하지 않았습니다(재시도 후 시간 초과). 게이트웨이 연결과 장비 상태를 확인하세요";
        anomalyRepository.save(Anomaly.openForDevice(
                event.deviceId(), AnomalyRuleName.COMMAND_TIMEOUT, AnomalySeverity.INFO, message, event.at()));
        log.info("이상 발생: {} COMMAND_TIMEOUT ({})", event.deviceId(), event.commandId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onAnswered(RemoteCommandAnsweredEvent event) {
        findOpen(event.deviceId()).ifPresent(open -> {
            open.resolve(event.at());
            log.info("이상 해결: {} COMMAND_TIMEOUT ({})", event.deviceId(), event.lateAck() ? "늦은 응답" : "다음 명령 정상");
        });
    }

    private Optional<Anomaly> findOpen(String deviceId) {
        return anomalyRepository.findByDeviceIdAndRuleAndStatus(deviceId, AnomalyRuleName.COMMAND_TIMEOUT, AnomalyStatus.OPEN);
    }
}
