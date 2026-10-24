package vent.servicerequest.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.servicerequest.domain.ServiceRequest;
import vent.servicerequest.repository.SituationStatsRepository;
import vent.servicerequest.rule.SituationDevice;
import vent.servicerequest.rule.SituationSiteProfile;
import vent.servicerequest.rule.SituationStats;
import vent.servicerequest.rule.SituationSummary;
import vent.servicerequest.rule.SituationSummaryCalculator;
import vent.servicerequest.rule.SituationTelemetryStats;
import vent.site.domain.Device;
import vent.site.domain.Site;
import vent.site.domain.type.DeviceType;
import vent.site.repository.SiteRepository;

/**
 * 상황 요약 만들기 (스펙 8.2, #10).
 *
 * <p>A/S 신청의 현장에서 최근 7일 통계를 모아 {@link SituationSummaryCalculator}로 지표를 계산한다. 측정값은 신청에
 * 적힌 장비가 환기장치면 그 장비, 아니면 현장의 첫 환기장치에서 읽는다. 필터 차압 추세의 시작값은 기간의 첫 7분의 1
 * (7일이면 첫날) 평균이다. 시연에서 기간을 줄여도 시작값과 최근 값이 구분되게 기간에 비례시킨다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Service
public class SituationSummaryService {

    private static final int FIRST_PERIOD_DIVISOR = 7;

    private final SiteRepository siteRepository;
    private final SituationStatsRepository statsRepository;
    private final SituationSummaryCalculator calculator;
    private final AssessmentProperties properties;

    public SituationSummaryService(SiteRepository siteRepository, SituationStatsRepository statsRepository,
                                   SituationSummaryCalculator calculator, AssessmentProperties properties) {
        this.siteRepository = siteRepository;
        this.statsRepository = statsRepository;
        this.calculator = calculator;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SituationSummary summarize(ServiceRequest request, OffsetDateTime now) {
        Site site = siteRepository.findById(request.getSiteId())
                .orElseThrow(() -> new NoSuchElementException("현장 없음: " + request.getSiteId()));
        OffsetDateTime since = now.minus(properties.window());
        Duration firstPeriod = properties.window().dividedBy(FIRST_PERIOD_DIVISOR);

        List<Device> devices = site.getDevices().stream().sorted(Comparator.comparing(Device::getId)).toList();
        SituationSiteProfile profile = new SituationSiteProfile(site.getRegion(), site.getFloorAreaM2(), site.getOccupants(),
                site.getTargetAirflowCmh(), site.minimumAirflowCmh(),
                devices.stream().map(d -> new SituationDevice(d.getType().name(), d.getModel())).toList());

        SituationTelemetryStats telemetry = ventilatorId(request, devices)
                .map(id -> statsRepository.telemetry(id, since, now, firstPeriod))
                .orElse(SituationTelemetryStats.NONE);

        return calculator.summarize(new SituationStats(profile, properties.window(), properties.telemetryInterval(),
                telemetry, statsRepository.hoodInterlock(site.getId(), since), statsRepository.offlineCount(site.getId(), since),
                statsRepository.openAnomalies(site.getId()), statsRepository.closedAnomalies(site.getId(), since)));
    }

    /** 신청 장비가 환기장치면 그것, 아니면 ID가 가장 앞선 환기장치. */
    private static Optional<String> ventilatorId(ServiceRequest request, List<Device> devices) {
        List<Device> ventilators = devices.stream().filter(d -> d.getType() == DeviceType.VENTILATOR).toList();
        return ventilators.stream().filter(d -> d.getId().equals(request.getDeviceId())).findFirst()
                .or(() -> ventilators.stream().findFirst())
                .map(Device::getId);
    }
}
