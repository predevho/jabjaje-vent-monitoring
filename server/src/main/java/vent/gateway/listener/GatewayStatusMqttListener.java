package vent.gateway.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import vent.gateway.dto.request.GatewayStatusMessage;
import vent.gateway.service.GatewayConnectionService;
import vent.gateway.service.GatewayStatusResult;
import vent.global.mqtt.MqttHandleResult;
import vent.global.mqtt.MqttMessageHandler;

/**
 * 게이트웨이 상태 MQTT 수신 처리기.
 *
 * <p>{@code jabjaje/{현장ID}/{게이트웨이ID}/status} 토픽을 구독한다. 게이트웨이가 보내는 online과,
 * 연결이 끊겼을 때 브로커가 대신 보내는 offline(Last Will)을 받는다(스펙 4.1, 6.3).
 * 빈 메시지는 retained 메시지를 지우는 신호라 무시한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@Component
public class GatewayStatusMqttListener implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayStatusMqttListener.class);
    private static final String TOPIC_FILTER = "jabjaje/+/+/status";

    private final ObjectMapper objectMapper;
    private final GatewayConnectionService connectionService;

    public GatewayStatusMqttListener(ObjectMapper objectMapper, GatewayConnectionService connectionService) {
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
    }

    @Override
    public String topicFilter() {
        return TOPIC_FILTER;
    }

    @Override
    public MqttHandleResult handle(String topic, byte[] payload, boolean retained) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String[] levels = topic.split("/");
        if (levels.length != 4 || payload.length == 0) {
            return MqttHandleResult.DONE;
        }

        GatewayStatusMessage message;
        try {
            message = objectMapper.readValue(payload, GatewayStatusMessage.class);
        } catch (IOException e) {
            log.warn("게이트웨이 상태 메시지를 해석할 수 없음, 버림: {} ({})", topic, e.getMessage());
            return MqttHandleResult.DONE;
        }

        GatewayStatusResult result;
        try {
            result = connectionService.applyStatus(levels[1], levels[2], message, retained, receivedAt);
        } catch (DataAccessException | TransactionException e) {
            MqttHandleResult failure = MqttHandleResult.forFailure(e);
            log.warn("게이트웨이 상태 반영 실패 ({}): {} ({})", failure, topic, e.getMessage());
            return failure;
        }
        if (result != GatewayStatusResult.APPLIED) {
            log.warn("게이트웨이 상태 반영 안 함 ({}): {}", result, topic);
        }
        return MqttHandleResult.DONE;
    }
}
