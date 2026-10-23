package vent.telemetry.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vent.telemetry.dto.response.TelemetryResponse;
import vent.telemetry.service.TelemetryQueryService;

/**
 * 측정값 조회 API.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@RestController
public class TelemetryController {

    private final TelemetryQueryService telemetryQueryService;

    public TelemetryController(TelemetryQueryService telemetryQueryService) {
        this.telemetryQueryService = telemetryQueryService;
    }

    /** 장비마다 최신 측정값 하나씩. 대시보드가 30초마다 부른다. */
    @GetMapping("/api/telemetry/latest")
    public List<TelemetryResponse> latest() {
        return telemetryQueryService.findLatestPerDevice();
    }

    /** 한 장비의 최근 측정값 목록 (최근 측정된 것부터). */
    @GetMapping("/api/devices/{deviceId}/telemetry")
    public List<TelemetryResponse> recent(@PathVariable String deviceId,
                                          @RequestParam(defaultValue = "100") int limit) {
        return telemetryQueryService.findRecent(deviceId, limit);
    }
}
