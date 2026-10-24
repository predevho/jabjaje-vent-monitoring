package vent.hood.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import vent.global.mqtt.MqttHandleResult;
import vent.global.mqtt.MqttMessageHandler;
import vent.hood.dto.request.DeviceEventMessage;
import vent.hood.service.DeviceEventIngestResult;
import vent.hood.service.DeviceEventIngestService;

/**
 * 장비 이벤트 MQTT 수신 처리기.
 *
 * <p>{@code jabjaje/{현장ID}/{장비ID}/event} 토픽을 구독해 VEMC의 후드 ON/OFF를 받는다(스펙 4.1).
 * 다시 받아도 결과가 같은 실패는 버리고, 잠깐의 DB 문제만 다시 받는다(ADR-0012).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Component
public class DeviceEventMqttListener implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventMqttListener.class);
    private static final String TOPIC_FILTER = "jabjaje/+/+/event";

    private final ObjectMapper objectMapper;
    private final DeviceEventIngestService ingestService;

    public DeviceEventMqttListener(ObjectMapper objectMapper, DeviceEventIngestService ingestService) {
        this.objectMapper = objectMapper;
        this.ingestService = ingestService;
    }

    @Override
    public String topicFilter() {
        return TOPIC_FILTER;
    }

    @Override
    public MqttHandleResult handle(String topic, byte[] payload, boolean retained) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String[] levels = topic.split("/");
        if (levels.length != 4) {
            log.warn("이벤트 토픽 형식이 아님, 버림: {}", topic);
            return MqttHandleResult.DONE;
        }

        DeviceEventMessage message;
        try {
            message = objectMapper.readValue(payload, DeviceEventMessage.class);
        } catch (IOException e) {
            log.warn("이벤트 메시지를 해석할 수 없음, 버림: {} ({})", topic, e.getMessage());
            return MqttHandleResult.DONE;
        }

        DeviceEventIngestResult result;
        try {
            result = ingestService.ingest(levels[1], levels[2], message, receivedAt);
        } catch (DataAccessException | TransactionException e) {
            MqttHandleResult failure = MqttHandleResult.forFailure(e);
            log.warn("이벤트 저장 실패 ({}): 토픽 {}, messageId {} ({})", failure, topic, message.messageId(), e.getMessage());
            return failure;
        }

        if (result == DeviceEventIngestResult.STORED) {
            log.info("장비 이벤트 저장: {} {} ({})", levels[2], message.type(), message.occurredAt());
        } else {
            log.warn("장비 이벤트 저장 안 함 ({}): 토픽 {}, messageId {}", result, topic, message.messageId());
        }
        return MqttHandleResult.DONE;
    }
}
