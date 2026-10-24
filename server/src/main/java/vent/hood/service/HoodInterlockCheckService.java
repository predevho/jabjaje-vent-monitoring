package vent.hood.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vent.hood.domain.DeviceEvent;
import vent.hood.domain.type.DeviceEventType;
import vent.hood.repository.DeviceEventRepository;
import vent.hood.rule.HoodInterlockInput;
import vent.hood.rule.HoodInterlockJudge;
import vent.hood.rule.HoodInterlockJudgement;
import vent.hood.rule.HoodInterlockProperties;
import vent.hood.rule.HoodInterlockReading;
import vent.site.domain.Device;
import vent.site.domain.type.DeviceType;
import vent.site.repository.DeviceRepository;
import vent.telemetry.domain.Telemetry;
import vent.telemetry.domain.type.TelemetryQuality;
import vent.telemetry.repository.TelemetryRepository;

/**
 * 후드 연동 확인 유스케이스 (스펙 6.2, #7).
 *
 * <p>확인 전 HOOD_ON을 하나씩 잠가서 읽고, 같은 현장 환기장치의 직전 측정값과 60초 뒤 첫 측정값을 찾아 확인한다.
 * 측정값은 품질 OK만 쓰고 늦은 측정값도 쓴다(ADR-0003). 결과를 이벤트에 기록하고 같은 트랜잭션에서
 * {@link HoodInterlockJudgedEvent}를 발행한다. 이벤트마다 트랜잭션을 따로 써서, 하나가 실패해도 나머지는 확인된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Service
public class HoodInterlockCheckService {

    private static final Logger log = LoggerFactory.getLogger(HoodInterlockCheckService.class);

    private final DeviceEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final HoodInterlockJudge judge;
    private final HoodInterlockProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public HoodInterlockCheckService(DeviceEventRepository eventRepository, DeviceRepository deviceRepository,
                                     TelemetryRepository telemetryRepository, HoodInterlockJudge judge,
                                     HoodInterlockProperties properties, ApplicationEventPublisher eventPublisher,
                                     PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.judge = judge;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 확인 전 HOOD_ON을 모두 확인해 본다. 60초 뒤 측정값이 아직 없는 것은 그대로 두고 다음 검사에서 다시 본다.
     *
     * @param now 검사 시각. 스케줄러는 지금 시각을, 테스트는 원하는 시각을 넣는다
     */
    public void checkPending(OffsetDateTime now) {
        for (Long eventId : eventRepository.findPendingHoodOnIds()) {
            try {
                transactionTemplate.executeWithoutResult(status -> eventRepository.findByIdForUpdate(eventId)
                        .filter(event -> !event.isJudged())
                        .ifPresent(event -> judgeOne(event, now)));
            } catch (RuntimeException e) {
                log.error("후드 연동 확인 실패, 다음 검사에서 다시 시도: 이벤트 {}", eventId, e);
            }
        }
    }

    private void judgeOne(DeviceEvent hoodOn, OffsetDateTime now) {
        judge.judge(inputFor(hoodOn), now).ifPresent(judgement -> record(hoodOn, judgement, now));
    }

    /**
     * 확인 재료를 모은다. 같은 현장에 환기장치가 없으면 측정값 없이 넘겨, 확인 클래스가 기다리다 24시간 뒤 확인 보류로 닫는다.
     */
    private HoodInterlockInput inputFor(DeviceEvent hoodOn) {
        Device vemc = deviceRepository.findById(hoodOn.getDeviceId()).orElseThrow();
        OffsetDateTime hoodOnAt = hoodOn.getOccurredAt();
        OffsetDateTime hoodOffAt = eventRepository
                .findFirstByDeviceIdAndTypeAndOccurredAtAfterOrderByOccurredAtAsc(vemc.getId(), DeviceEventType.HOOD_OFF, hoodOnAt)
                .map(DeviceEvent::getOccurredAt)
                .orElse(null);
        boolean hoodAlreadyOn = eventRepository
                .findFirstByDeviceIdAndOccurredAtBeforeOrderByOccurredAtDesc(vemc.getId(), hoodOnAt)
                .map(previous -> previous.getType() == DeviceEventType.HOOD_ON)
                .orElse(false);

        Optional<String> ventilatorId = deviceRepository
                .findFirstBySiteIdAndTypeOrderByIdAsc(vemc.getSite().getId(), DeviceType.VENTILATOR)
                .map(Device::getId);
        HoodInterlockReading before = ventilatorId.flatMap(id -> telemetryRepository
                        .findFirstByDeviceIdAndQualityAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(id, TelemetryQuality.OK, hoodOnAt))
                .map(HoodInterlockCheckService::toReading)
                .orElse(null);
        HoodInterlockReading after = ventilatorId.flatMap(id -> telemetryRepository
                        .findFirstByDeviceIdAndQualityAndMeasuredAtGreaterThanEqualOrderByMeasuredAtAsc(
                                id, TelemetryQuality.OK, hoodOnAt.plus(properties.responseWait())))
                .map(HoodInterlockCheckService::toReading)
                .orElse(null);
        return new HoodInterlockInput(hoodOnAt, hoodOffAt, hoodAlreadyOn, before, after);
    }

    private void record(DeviceEvent hoodOn, HoodInterlockJudgement judgement, OffsetDateTime now) {
        hoodOn.recordJudgement(judgement.result(), now);
        log.info("후드 연동 확인: {} {} ({} → {}CMH)", hoodOn.getDeviceId(), judgement.result(),
                judgement.beforeSupplyCmh(), judgement.afterSupplyCmh());
        eventPublisher.publishEvent(new HoodInterlockJudgedEvent(hoodOn.getDeviceId(), judgement.result(),
                judgement.beforeSupplyCmh(), judgement.afterSupplyCmh(), properties.responseWait(), judgement.at()));
    }

    private static HoodInterlockReading toReading(Telemetry telemetry) {
        return new HoodInterlockReading(telemetry.getMeasuredAt(), telemetry.getSupplyCmh());
    }
}
