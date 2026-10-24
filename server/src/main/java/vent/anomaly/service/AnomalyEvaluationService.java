package vent.anomaly.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.anomaly.repository.AnomalyRepository;
import vent.anomaly.rule.AnomalyContinuityProperties;
import vent.anomaly.rule.AnomalyRuleInput;
import vent.anomaly.rule.AnomalyRuleOutcome;
import vent.anomaly.rule.AnomalyRuleTelemetry;
import vent.anomaly.rule.DeviceAnomalyRule;
import vent.site.domain.Device;
import vent.site.repository.DeviceRepository;
import vent.telemetry.domain.Telemetry;
import vent.telemetry.domain.type.TelemetryQuality;
import vent.telemetry.repository.TelemetryRepository;

/**
 * 장비 이상 평가 유스케이스.
 *
 * <p>장비 한 대의 품질 OK 측정값을 측정 시각 기준으로 다시 조회해 그 장비 종류에 해당하는 규칙을 평가하고,
 * 결과를 이상으로 반영한다. 방금 들어온 값이 아니라 저장된 최근 값으로 판단하므로, 늦은 측정값이 순서 없이 들어와도
 * 판단이 흔들리지 않는다(스펙 6.2).
 *
 * <p>측정값은 규칙들이 밝힌 조회 범위 중 가장 긴 만큼 가져온다(#5). 범위 안에 OK 측정값이 없으면(범위 밖 값만 들어오는 경우)
 * 판단하지 않는다. 범위 밖의 오래된 값으로 지금 상태를 해제하면 안 되기 때문이다.
 *
 * <p>중복 방지는 여기서 한다: 발생인데 이미 열려 있으면 그대로(더 무거운 심각도면 올림), 해제인데 열린 것이 없으면 그대로.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@Service
public class AnomalyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEvaluationService.class);

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final AnomalyRepository anomalyRepository;
    private final List<DeviceAnomalyRule> rules;
    private final AnomalyContinuityProperties continuity;

    public AnomalyEvaluationService(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository,
                                    AnomalyRepository anomalyRepository, List<DeviceAnomalyRule> rules,
                                    AnomalyContinuityProperties continuity) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.anomalyRepository = anomalyRepository;
        this.rules = rules;
        this.continuity = continuity;
    }

    /**
     * 장비 한 대의 모든 규칙을 평가한다. 측정값 저장 트랜잭션과 따로, 새 트랜잭션에서 실행한다.
     *
     * @param deviceId 평가할 장비 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateDevice(String deviceId) {
        Optional<Device> found = deviceRepository.findById(deviceId);
        if (found.isEmpty()) {
            return;
        }
        Device device = found.get();
        List<DeviceAnomalyRule> applicable = rules.stream().filter(rule -> rule.appliesTo(device.getType())).toList();
        if (applicable.isEmpty()) {
            return;
        }
        AnomalyRuleInput input = new AnomalyRuleInput(deviceId, device.getModel(),
                device.getSite().getTargetAirflowCmh(), recentOkTelemetry(deviceId, lookbackOf(applicable)));
        for (DeviceAnomalyRule rule : applicable) {
            apply(deviceId, rule, rule.evaluate(input));
        }
    }

    /** 규칙들 중 가장 긴 조회 범위에, 구간 끊김을 판단할 여유(최대 간격)를 더한다. */
    private Duration lookbackOf(List<DeviceAnomalyRule> applicable) {
        Duration longest = applicable.stream().map(DeviceAnomalyRule::lookback).max(Duration::compareTo).orElse(Duration.ZERO);
        return longest.plus(continuity.maxGap());
    }

    private List<AnomalyRuleTelemetry> recentOkTelemetry(String deviceId, Duration lookback) {
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minus(lookback);
        return telemetryRepository
                .findByDeviceIdAndQualityAndMeasuredAtAfterOrderByMeasuredAtDesc(deviceId, TelemetryQuality.OK, since)
                .stream().map(AnomalyEvaluationService::toRuleTelemetry).toList();
    }

    private static AnomalyRuleTelemetry toRuleTelemetry(Telemetry telemetry) {
        return new AnomalyRuleTelemetry(telemetry.getMeasuredAt(), telemetry.getCo2Ppm(), telemetry.getSupplyCmh(),
                telemetry.getFilterPa(), telemetry.isBypass(), telemetry.isLate());
    }

    private void apply(String deviceId, DeviceAnomalyRule rule, AnomalyRuleOutcome outcome) {
        Optional<Anomaly> open = anomalyRepository.findByDeviceIdAndRuleAndStatus(deviceId, rule.name(), AnomalyStatus.OPEN);
        switch (outcome.kind()) {
            case RAISE -> {
                if (open.isEmpty()) {
                    anomalyRepository.save(Anomaly.openForDevice(
                            deviceId, rule.name(), outcome.severity(), outcome.message(), outcome.at()));
                    log.info("이상 발생: {} {} {} ({})", deviceId, rule.name(), outcome.severity(), outcome.message());
                } else if (open.get().escalate(outcome.severity(), outcome.message())) {
                    log.info("이상 심각도 올림: {} {} → {} ({})", deviceId, rule.name(), outcome.severity(), outcome.message());
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
