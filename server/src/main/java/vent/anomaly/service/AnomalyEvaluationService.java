package vent.anomaly.service;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.anomaly.repository.AnomalyRepository;
import vent.anomaly.rule.DeviceAnomalyRule;
import vent.anomaly.rule.AnomalyRuleInput;
import vent.anomaly.rule.AnomalyRuleTelemetry;
import vent.anomaly.rule.AnomalyRuleOutcome;
import vent.site.domain.Device;
import vent.site.repository.DeviceRepository;
import vent.telemetry.domain.type.TelemetryQuality;
import vent.telemetry.repository.TelemetryRepository;

/**
 * 장비 이상 평가 유스케이스.
 *
 * <p>장비 한 대의 품질 OK 측정값을 측정 시각 기준으로 다시 조회해 그 장비 종류에 해당하는 규칙을 평가하고, 결과를 이상으로 반영한다.
 * 방금 들어온 값이 아니라 저장된 최신 값으로 판단하므로, 늦은 측정값이 순서 없이 들어와도 판단이 흔들리지 않는다(스펙 6.2).
 *
 * <p>중복 방지는 여기서 한다: 발생인데 이미 열려 있으면 그대로, 해제인데 열린 것이 없으면 그대로.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@Service
public class AnomalyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEvaluationService.class);
    /** 규칙에 넘길 최근 측정값 개수. 지금 규칙(필터 막힘)은 최신 값만 본다. 지속 시간을 보는 규칙(#5)이 생기면 늘린다. */
    private static final int RECENT_READINGS = 1;

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final AnomalyRepository anomalyRepository;
    private final List<DeviceAnomalyRule> rules;

    public AnomalyEvaluationService(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository,
                                    AnomalyRepository anomalyRepository, List<DeviceAnomalyRule> rules) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.anomalyRepository = anomalyRepository;
        this.rules = rules;
    }

    /**
     * 장비 한 대의 모든 규칙을 평가한다. 측정값 저장 트랜잭션과 따로, 새 트랜잭션에서 실행한다.
     *
     * @param deviceId 평가할 장비 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateDevice(String deviceId) {
        Optional<Device> device = deviceRepository.findById(deviceId);
        if (device.isEmpty()) {
            return;
        }
        List<DeviceAnomalyRule> applicable = rules.stream().filter(rule -> rule.appliesTo(device.get().getType())).toList();
        if (applicable.isEmpty()) {
            return;
        }
        AnomalyRuleInput input = new AnomalyRuleInput(deviceId, device.get().getModel(), recentOkTelemetry(deviceId));
        for (DeviceAnomalyRule rule : applicable) {
            apply(deviceId, rule, rule.evaluate(input));
        }
    }

    private List<AnomalyRuleTelemetry> recentOkTelemetry(String deviceId) {
        return telemetryRepository
                .findByDeviceIdAndQualityOrderByMeasuredAtDesc(deviceId, TelemetryQuality.OK, Limit.of(RECENT_READINGS))
                .stream()
                .map(telemetry -> new AnomalyRuleTelemetry(telemetry.getMeasuredAt(), telemetry.getFilterPa()))
                .toList();
    }

    private void apply(String deviceId, DeviceAnomalyRule rule, AnomalyRuleOutcome outcome) {
        Optional<Anomaly> open = anomalyRepository.findByDeviceIdAndRuleAndStatus(deviceId, rule.name(), AnomalyStatus.OPEN);
        switch (outcome.kind()) {
            case RAISE -> {
                if (open.isEmpty()) {
                    anomalyRepository.save(Anomaly.openForDevice(
                            deviceId, rule.name(), outcome.severity(), outcome.message(), outcome.at()));
                    log.info("이상 발생: {} {} ({})", deviceId, rule.name(), outcome.message());
                }
            }
            case CLEAR -> open.ifPresent(anomaly -> {
                anomaly.resolve(outcome.at());
                log.info("이상 해결: {} {}", deviceId, rule.name());
            });
            case UNCHANGED -> {
                // 지금 상태를 그대로 둔다.
            }
        }
    }
}
