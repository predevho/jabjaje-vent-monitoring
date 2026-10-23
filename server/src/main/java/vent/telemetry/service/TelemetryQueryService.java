package vent.telemetry.service;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.telemetry.dto.response.TelemetryResponse;
import vent.telemetry.repository.TelemetryRepository;

/**
 * 측정값 조회 유스케이스.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@Service
public class TelemetryQueryService {

    /** 한 번에 돌려줄 수 있는 최대 건수. */
    public static final int MAX_LIMIT = 500;

    private final TelemetryRepository telemetryRepository;

    public TelemetryQueryService(TelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    /** 장비마다 가장 최근에 측정된 측정값 하나씩. */
    @Transactional(readOnly = true)
    public List<TelemetryResponse> findLatestPerDevice() {
        return telemetryRepository.findLatestPerDevice().stream()
                .map(TelemetryResponse::from)
                .toList();
    }

    /** 한 장비의 측정값을 최근 측정된 것부터 최대 {@code limit}건. */
    @Transactional(readOnly = true)
    public List<TelemetryResponse> findRecent(String deviceId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return telemetryRepository.findByDeviceIdOrderByMeasuredAtDesc(deviceId, Limit.of(safeLimit)).stream()
                .map(TelemetryResponse::from)
                .toList();
    }
}
