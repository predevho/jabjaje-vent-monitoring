package vent.command.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import vent.command.dto.request.CommandAckMessage;
import vent.command.service.CommandAckResult;
import vent.command.service.CommandAckService;
import vent.global.mqtt.MqttHandleResult;
import vent.global.mqtt.MqttMessageHandler;

/**
 * 원격 명령 응답 MQTT 수신 처리기.
 *
 * <p>{@code jabjaje/{현장ID}/{장비ID}/command/ack} 토픽을 구독한다(스펙 4.1). 잠깐의 DB 문제만 다시 받는다(ADR-0012).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Component
public class CommandAckMqttListener implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandAckMqttListener.class);
    private static final String TOPIC_FILTER = "jabjaje/+/+/command/ack";

    private final ObjectMapper objectMapper;
    private final CommandAckService ackService;

    public CommandAckMqttListener(ObjectMapper objectMapper, CommandAckService ackService) {
        this.objectMapper = objectMapper;
        this.ackService = ackService;
    }

    @Override
    public String topicFilter() {
        return TOPIC_FILTER;
    }

    @Override
    public MqttHandleResult handle(String topic, byte[] payload, boolean retained) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String[] levels = topic.split("/");
        if (levels.length != 5) {
            log.warn("명령 응답 토픽 형식이 아님, 버림: {}", topic);
            return MqttHandleResult.DONE;
        }

        CommandAckMessage message;
        try {
            message = objectMapper.readValue(payload, CommandAckMessage.class);
        } catch (IOException e) {
            log.warn("명령 응답을 해석할 수 없음, 버림: {} ({})", topic, e.getMessage());
            return MqttHandleResult.DONE;
        }

        CommandAckResult result;
        try {
            result = ackService.apply(levels[1], levels[2], message, receivedAt);
        } catch (DataAccessException | TransactionException e) {
            MqttHandleResult failure = MqttHandleResult.forFailure(e);
            log.warn("명령 응답 반영 실패 ({}): {} ({})", failure, topic, e.getMessage());
            return failure;
        }
        if (result != CommandAckResult.RECORDED) {
            log.warn("명령 응답 반영 안 함 ({}): {}, commandId {}", result, topic, message.commandId());
        }
        return MqttHandleResult.DONE;
    }
}
