package vent.telemetry.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import vent.global.mqtt.MqttHandleResult;
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

    /**
     * 측정값 메시지 하나를 처리한다.
     *
     * <p>형식 오류, 검증 실패, 무결성 위반처럼 다시 받아도 결과가 같은 것은 {@code DONE}으로 버린다.
     * DB 연결 실패처럼 잠깐의 문제만 {@code RETRY_LATER}로 돌려서 브로커가 다시 보내게 한다(ADR-0012).
     */
    @Override
    public MqttHandleResult handle(String topic, byte[] payload) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String[] levels = topic.split("/");
        if (levels.length != 4) {
            log.warn("측정값 토픽 형식이 아님, 버림: {}", topic);
            return MqttHandleResult.DONE;
        }
        String siteId = levels[1];
        String deviceId = levels[2];

        TelemetryMessage message;
        try {
            message = objectMapper.readValue(payload, TelemetryMessage.class);
        } catch (IOException e) {
            log.warn("측정값 메시지를 해석할 수 없음, 버림: {} ({})", topic, e.getMessage());
            return MqttHandleResult.DONE;
        }

        TelemetryIngestResult result;
        try {
            result = ingestService.ingest(siteId, deviceId, message, receivedAt);
        } catch (TransientDataAccessException | DataAccessResourceFailureException | TransactionException e) {
            // DB 연결 실패, 일시적 잠금·시간 초과처럼 잠시 뒤 다시 하면 될 수 있는 오류
            log.warn("측정값 저장 실패(일시적 DB 문제), 나중에 다시 받음: 토픽 {}, messageId {} ({})",
                    topic, message.messageId(), e.getMessage());
            return MqttHandleResult.RETRY_LATER;
        } catch (DataAccessException e) {
            // 무결성 위반처럼 같은 메시지로 다시 해도 또 실패하는 오류. 다시 받으면 끝없이 돌아온다(poison message).
            log.error("측정값 저장 실패(다시 해도 같은 오류), 버림: 토픽 {}, messageId {}", topic, message.messageId(), e);
            return MqttHandleResult.DONE;
        }

        if (result == TelemetryIngestResult.STORED) {
            log.debug("측정값 저장: {} {}", deviceId, message.messageId());
        } else {
            log.warn("측정값 저장 안 함 ({}): 토픽 {}, messageId {}", result, topic, message.messageId());
        }
        return MqttHandleResult.DONE;
    }
}
