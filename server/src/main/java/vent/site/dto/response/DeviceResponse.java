package vent.site.dto.response;

import vent.site.domain.Device;
import vent.site.domain.DeviceType;

/**
 * 장비 응답.
 *
 * <p>현장 응답 안에 들어가는 장비 id, 종류, 모델명.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public record DeviceResponse(String id, DeviceType type, String model) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(device.getId(), device.getType(), device.getModel());
    }
}
