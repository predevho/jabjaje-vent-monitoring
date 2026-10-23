package vent.site.dto.response;

import java.math.BigDecimal;
import java.util.List;

import vent.site.domain.Site;
import vent.site.domain.type.TargetAirflowSource;

/**
 * 현장 응답.
 *
 * <p>엔티티를 그대로 내보내지 않고 화면에 필요한 현장 정보와 그 게이트웨이, 장비만 담는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public record SiteResponse(
        Long id,
        String name,
        String region,
        BigDecimal floorAreaM2,
        int occupants,
        int targetAirflowCmh,
        TargetAirflowSource targetAirflowSource,
        GatewayResponse gateway,
        List<DeviceResponse> devices) {

    public static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getId(),
                site.getName(),
                site.getRegion(),
                site.getFloorAreaM2(),
                site.getOccupants(),
                site.getTargetAirflowCmh(),
                site.getTargetAirflowSource(),
                GatewayResponse.from(site.getGateway()),
                site.getDevices().stream().map(DeviceResponse::from).toList());
    }
}
