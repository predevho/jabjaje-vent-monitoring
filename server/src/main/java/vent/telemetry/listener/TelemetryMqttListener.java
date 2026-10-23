package vent.telemetry.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vent.global.mqtt.MqttMessageHandler;
import vent.telemetry.dto.request.TelemetryMessage;
import vent.telemetry.service.TelemetryIngestResult;
import vent.telemetry.service.TelemetryIngestService;

/**
 * 측정값 MQTT 수신 처리기.
 *
 * <p>{@code jabjaje/{현장ID}/{장비ID}/telemetry} 토픽을 구독해 메시지를 해석하고 {@link TelemetryIngestService}에 넘긴다.
 * HTTP 입구(controller)와 구분해 메시지 입구는 listener 레이어에 둔다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@Component
public class TelemetryMqttListener implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMqttListener.class);
    private static final String TOPIC_FILTER = "jabjaje/+/+/telemetry";

    private final ObjectMapper objectMapper;
    private final TelemetryIngestService ingestService;

    public TelemetryMqttListener(ObjectMapper objectMapper, TelemetryIngestService ingestService) {
        this.objectMapper = objectMapper;
        this.ingestService = ingestService;
    }

    @Override
    public String topicFilter() {
        return TOPIC_FILTER;
    }

    @Override
    public void handle(String topic, byte[] payload) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String[] levels = topic.split("/");
        if (levels.length != 4) {
            log.warn("측정값 토픽 형식이 아님: {}", topic);
            return;
        }
        String siteId = levels[1];
        String deviceId = levels[2];

        TelemetryMessage message;
        try {
            message = objectMapper.readValue(payload, TelemetryMessage.class);
        } catch (IOException e) {
            log.warn("측정값 메시지를 해석할 수 없음: {} ({})", topic, e.getMessage());
            return;
        }

        TelemetryIngestResult result = ingestService.ingest(siteId, deviceId, message, receivedAt);
        if (result == TelemetryIngestResult.STORED) {
            log.debug("측정값 저장: {} {}", deviceId, message.messageId());
        } else {
            log.warn("측정값 저장 안 함 ({}): 토픽 {}, messageId {}", result, topic, message.messageId());
        }
    }
}
