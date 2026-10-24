package vent.hood.service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.hood.domain.type.DeviceEventType;
import vent.hood.dto.request.DeviceEventMessage;
import vent.hood.repository.DeviceEventInsertParams;
import vent.hood.repository.DeviceEventRepository;
import vent.site.domain.Device;
import vent.site.domain.type.DeviceType;
import vent.site.repository.DeviceRepository;

/**
 * 장비 이벤트 수신 유스케이스.
 *
 * <p>토픽에서 읽은 현장·장비 ID와 메시지를 확인한 뒤 이벤트를 저장한다. 측정값 수신과 같은 규칙이다(스펙 4.3, 9.2 S3).
 * 확인은 여기서 하지 않고, 30초 주기 검사가 저장된 이벤트를 찾아 한다. 60초 뒤 측정값이 와야 확인할 수 있어서다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Service
public class DeviceEventIngestService {

    private final DeviceRepository deviceRepository;
    private final DeviceEventRepository eventRepository;

    public DeviceEventIngestService(DeviceRepository deviceRepository, DeviceEventRepository eventRepository) {
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * 이벤트 하나를 처리한다.
     *
     * @param topicSiteId   토픽의 현장 ID
     * @param topicDeviceId 토픽의 장비 ID
     * @param message       메시지 본문
     * @param receivedAt    서버가 받은 시각
     * @return 처리 결과. 저장되지 않았으면 그 이유
     */
    @Transactional
    public DeviceEventIngestResult ingest(String topicSiteId, String topicDeviceId, DeviceEventMessage message,
                                          OffsetDateTime receivedAt) {
        if (!message.hasAllRequiredFields() || !isKnownType(message.type())) {
            return DeviceEventIngestResult.INVALID;
        }
        if (!topicDeviceId.equals(message.deviceId())) {
            return DeviceEventIngestResult.DEVICE_ID_MISMATCH;
        }
        Optional<Device> found = deviceRepository.findById(topicDeviceId);
        if (found.isEmpty()) {
            return DeviceEventIngestResult.UNKNOWN_DEVICE;
        }
        Device device = found.get();
        if (!device.belongsTo(topicSiteId)) {
            return DeviceEventIngestResult.SITE_MISMATCH;
        }
        if (device.getType() != DeviceType.VEMC) {
            return DeviceEventIngestResult.NOT_VEMC;
        }

        int inserted = eventRepository.insertIgnoringDuplicate(new DeviceEventInsertParams(
                topicDeviceId, message.messageId(), message.type(), message.occurredAt(), receivedAt));
        return inserted == 0 ? DeviceEventIngestResult.DUPLICATE : DeviceEventIngestResult.STORED;
    }

    private static boolean isKnownType(String type) {
        return Arrays.stream(DeviceEventType.values()).anyMatch(known -> known.name().equals(type));
    }
}
