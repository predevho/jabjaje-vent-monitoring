package vent.anomaly.service;

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
import vent.hood.service.HoodInterlockJudgedEvent;

/**
 * VEMC 무반응 이상 유스케이스 ({@code VEMC_NO_RESPONSE}, 스펙 6.1).
 *
 * <p>후드 연동이 무반응으로 확인되면 VEMC에 이상을 열고, 같은 VEMC의 다음 연동이 정상으로 확인되면 해결한다.
 * 확인 보류는 이상을 만들지도 해결하지도 않는다. 확인 결과를 기록한 트랜잭션에 함께 들어가야 해서 {@code MANDATORY}다.
 * 결과만 기록되고 이상이 빠지면 그 이벤트는 다시 확인하지 않기 때문이다(ADR-0014와 같은 이유).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Service
public class VemcNoResponseAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(VemcNoResponseAnomalyService.class);

    private final AnomalyRepository anomalyRepository;

    public VemcNoResponseAnomalyService(AnomalyRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onJudged(HoodInterlockJudgedEvent event) {
        switch (event.result()) {
            case NO_RESPONSE -> open(event);
            case CONFIRMED -> resolve(event);
            case INCONCLUSIVE -> {
                // 판단할 수 없었으므로 지금 상태를 그대로 둔다.
            }
        }
    }

    private void open(HoodInterlockJudgedEvent event) {
        if (findOpen(event.vemcDeviceId()) != null) {
            return;
        }
        String message = "후드를 켠 뒤 %d초가 지나도 급기가 오르지 않았습니다 (%d → %dCMH). VEMC 배선과 설정을 확인하세요"
                .formatted(event.responseWait().toSeconds(), event.beforeSupplyCmh(), event.afterSupplyCmh());
        anomalyRepository.save(Anomaly.openForDevice(
                event.vemcDeviceId(), AnomalyRuleName.VEMC_NO_RESPONSE, AnomalySeverity.WARN, message, event.at()));
        log.info("이상 발생: {} VEMC_NO_RESPONSE ({})", event.vemcDeviceId(), message);
    }

    private void resolve(HoodInterlockJudgedEvent event) {
        Anomaly open = findOpen(event.vemcDeviceId());
        // 이상이 열린 뒤의 정상 연동만 해결로 본다. 끊긴 동안 쌓였던 옛날 HOOD_ON의 확인이 늦게 끝나도 해결 시각이 뒤집히지 않게.
        if (open != null && event.at().isAfter(open.getOpenedAt())) {
            open.resolve(event.at());
            log.info("이상 해결: {} VEMC_NO_RESPONSE (다음 연동 정상, {} → {}CMH)",
                    event.vemcDeviceId(), event.beforeSupplyCmh(), event.afterSupplyCmh());
        }
    }

    private Anomaly findOpen(String vemcDeviceId) {
        return anomalyRepository
                .findByDeviceIdAndRuleAndStatus(vemcDeviceId, AnomalyRuleName.VEMC_NO_RESPONSE, AnomalyStatus.OPEN)
                .orElse(null);
    }
}
