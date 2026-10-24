package vent.anomaly.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.anomaly.dto.response.AnomalyResponse;
import vent.anomaly.repository.AnomalyRepository;
import vent.site.domain.Device;
import vent.site.domain.Site;
import vent.site.repository.DeviceRepository;

/**
 * 이상 조회 유스케이스.
 *
 * <p>상태별 이상 목록에 대상 장비의 현장 이름을 붙여 돌려준다. 게이트웨이 대상 이상은 #6에서 현장 정보를 붙인다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@Service
public class AnomalyQueryService {

    private final AnomalyRepository anomalyRepository;
    private final DeviceRepository deviceRepository;

    public AnomalyQueryService(AnomalyRepository anomalyRepository, DeviceRepository deviceRepository) {
        this.anomalyRepository = anomalyRepository;
        this.deviceRepository = deviceRepository;
    }

    /** 그 상태의 이상을 최근에 생긴 것부터. */
    @Transactional(readOnly = true)
    public List<AnomalyResponse> findByStatus(AnomalyStatus status) {
        List<Anomaly> anomalies = anomalyRepository.findByStatusOrderByOpenedAtDesc(status);
        List<String> deviceIds = anomalies.stream().map(Anomaly::getDeviceId).filter(Objects::nonNull).distinct().toList();
        Map<String, Site> siteByDevice = deviceRepository.findAllWithSiteByIdIn(deviceIds).stream()
                .collect(Collectors.toMap(Device::getId, Device::getSite, (a, b) -> a));
        return anomalies.stream()
                .map(anomaly -> AnomalyResponse.of(anomaly, siteByDevice.get(anomaly.getDeviceId())))
                .toList();
    }
}
