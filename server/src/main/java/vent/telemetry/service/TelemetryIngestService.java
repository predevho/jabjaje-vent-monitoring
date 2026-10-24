package vent.telemetry.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.site.domain.Device;
import vent.site.repository.DeviceRepository;
import vent.telemetry.dto.request.TelemetryMessage;
import vent.telemetry.repository.TelemetryInsertParams;
import vent.telemetry.repository.TelemetryRepository;

/**
 * 측정값 수신 유스케이스.
 *
 * <p>토픽에서 읽은 현장·장비 ID와 메시지를 확인한 뒤 측정값을 저장하고, 장비의 마지막 수신 시각을 갱신한다.
 * 장비 ID는 브로커 ACL이 묶는 토픽 값을 믿는다(스펙 4.3).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@Service
public class TelemetryIngestService {

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final TelemetryQualityClassifier qualityClassifier;

    public TelemetryIngestService(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository,
                                  TelemetryQualityClassifier qualityClassifier) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.qualityClassifier = qualityClassifier;
    }

    /**
     * 측정값 하나를 처리한다.
     *
     * @param topicSiteId   토픽의 현장 ID
     * @param topicDeviceId 토픽의 장비 ID
     * @param message       메시지 본문
     * @param receivedAt    서버가 받은 시각
     * @return 처리 결과. 저장되지 않았으면 그 이유
     */
    @Transactional
    public TelemetryIngestResult ingest(String topicSiteId, String topicDeviceId, TelemetryMessage message, OffsetDateTime receivedAt) {
        if (!message.hasAllRequiredFields()) {
            return TelemetryIngestResult.INVALID;
        }
        if (!topicDeviceId.equals(message.deviceId())) {
            return TelemetryIngestResult.DEVICE_ID_MISMATCH;
        }
        Optional<Device> found = deviceRepository.findById(topicDeviceId);
        if (found.isEmpty()) {
            return TelemetryIngestResult.UNKNOWN_DEVICE;
        }
        Device device = found.get();
        if (!device.belongsTo(topicSiteId)) {
            return TelemetryIngestResult.SITE_MISMATCH;
        }

        // 범위 밖, 시계 오차 측정값도 버리지 않고 표시해서 저장한다. 판단에서만 뺀다 (스펙 5.2).
        TelemetryClassification classification = qualityClassifier.classify(message, receivedAt);
        int inserted = telemetryRepository.insertIgnoringDuplicate(
                toInsertParams(topicDeviceId, message, receivedAt, classification));
        if (inserted == 0) {
            return TelemetryIngestResult.DUPLICATE;
        }
        device.markReceived(receivedAt);
        return TelemetryIngestResult.STORED;
    }

    /** 메시지에서 저장할 값을 만든다. 측정값 필드를 옮겨 담는 곳은 여기 한 곳뿐이다. */
    private static TelemetryInsertParams toInsertParams(String deviceId, TelemetryMessage message,
                                                        OffsetDateTime receivedAt, TelemetryClassification classification) {
        return new TelemetryInsertParams(
                deviceId,
                message.messageId(),
                message.measuredAt(),
                receivedAt,
                message.co2Ppm(),
                message.tempC(),
                message.humidityPct(),
                message.supplyCmh(),
                message.exhaustCmh(),
                message.filterPa(),
                message.bypass(),
                classification.quality().name(),
                classification.late());
    }
}
