package vent.anomaly.dto.response;

import java.time.OffsetDateTime;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.domain.type.AnomalyStatus;
import vent.site.domain.Site;

/**
 * 이상 응답.
 *
 * <p>이상 하나와 그 대상(현장, 장비 또는 게이트웨이). 시각은 판단 근거가 된 측정값의 측정 시각이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public record AnomalyResponse(
        Long id,
        AnomalyRuleName rule,
        AnomalySeverity severity,
        AnomalyStatus status,
        String message,
        Long siteId,
        String siteName,
        String deviceId,
        String gatewayId,
        OffsetDateTime openedAt,
        OffsetDateTime resolvedAt) {

    /** @param site 대상의 현장. 모르면 null */
    public static AnomalyResponse of(Anomaly anomaly, Site site) {
        return new AnomalyResponse(
                anomaly.getId(),
                anomaly.getRule(),
                anomaly.getSeverity(),
                anomaly.getStatus(),
                anomaly.getMessage(),
                site == null ? null : site.getId(),
                site == null ? null : site.getName(),
                anomaly.getDeviceId(),
                anomaly.getGatewayId(),
                anomaly.getOpenedAt(),
                anomaly.getResolvedAt());
    }
}
