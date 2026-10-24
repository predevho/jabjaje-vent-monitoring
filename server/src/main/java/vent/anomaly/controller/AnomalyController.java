package vent.anomaly.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.anomaly.dto.response.AnomalyResponse;
import vent.anomaly.service.AnomalyQueryService;

/**
 * 이상 조회 API.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyQueryService anomalyQueryService;

    public AnomalyController(AnomalyQueryService anomalyQueryService) {
        this.anomalyQueryService = anomalyQueryService;
    }

    /** 상태별 이상 목록 (기본: 열린 이상). 대시보드가 30초마다 부른다. */
    @GetMapping
    public List<AnomalyResponse> list(@RequestParam(defaultValue = "OPEN") AnomalyStatus status) {
        return anomalyQueryService.findByStatus(status);
    }
}
