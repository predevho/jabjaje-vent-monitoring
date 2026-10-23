package vent.site;

import java.math.BigDecimal;
import java.util.List;

/** 현장 목록 API 응답. 엔티티를 그대로 내보내지 않고 화면에 필요한 값만 담는다. */
record SiteResponse(
        Long id,
        String name,
        String region,
        BigDecimal floorAreaM2,
        int occupants,
        int targetAirflowCmh,
        TargetAirflowSource targetAirflowSource,
        GatewayResponse gateway,
        List<DeviceResponse> devices) {

    static SiteResponse from(Site site) {
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

    record GatewayResponse(String id, ConnectionState connectionState) {
        static GatewayResponse from(Gateway gateway) {
            return gateway == null ? null : new GatewayResponse(gateway.getId(), gateway.getConnectionState());
        }
    }

    record DeviceResponse(String id, DeviceType type, String model) {
        static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.getType(), device.getModel());
        }
    }
}
