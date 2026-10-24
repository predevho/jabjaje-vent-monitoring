package vent.command.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vent.command.domain.type.RemoteCommandType;
import vent.global.mqtt.MqttClientManager;

/**
 * 원격 명령 발행 (스펙 4.1, 4.2, 7.2, #8, #9).
 *
 * <p>{@code jabjaje/{현장ID}/{장비ID}/command}에 QoS 1, retained 없이 보낸다. MQTT 5 메시지 만료를 남은 시간만큼 건다.
 * 만료된 명령은 브로커가 장비에 넘기지 않고 버린다(재시도도 처음 만료 시각을 넘지 않는다). 이미 만료됐으면 보내지 않는다.
 * 발행이 실패하면 명령은 보냄으로 남고 재시도·시간 초과가 처리한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Service
public class RemoteCommandSender {

    private static final Logger log = LoggerFactory.getLogger(RemoteCommandSender.class);
    private static final int SCHEMA_VERSION = 1;

    private final MqttClientManager mqttClientManager;
    private final ObjectMapper objectMapper;

    public RemoteCommandSender(MqttClientManager mqttClientManager, ObjectMapper objectMapper) {
        this.mqttClientManager = mqttClientManager;
        this.objectMapper = objectMapper;
    }

    public void send(RemoteCommandIssuedEvent event) {
        long remainingSeconds = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), event.expiresAt()).toSeconds();
        if (remainingSeconds <= 0) {
            log.warn("원격 명령이 이미 만료되어 보내지 않음: {} {}", event.deviceId(), event.commandId());
            return;
        }
        String topic = "jabjaje/" + event.siteId() + "/" + event.deviceId() + "/command";
        try {
            mqttClientManager.publish(topic, objectMapper.writeValueAsBytes(message(event)), remainingSeconds);
            log.info("원격 명령 {}: {} {} {} (만료까지 {}초)", event.retry() ? "재시도" : "보냄", event.deviceId(),
                    RemoteCommandType.BOOST, event.commandId(), remainingSeconds);
        } catch (MqttException | RuntimeException e) {
            log.error("원격 명령 발행 실패, 보냄 상태로 남음: {} {}", event.deviceId(), event.commandId(), e);
        } catch (JsonProcessingException e) {
            log.error("원격 명령을 메시지로 바꿀 수 없음: {}", event.commandId(), e);
        }
    }

    private static Map<String, Object> message(RemoteCommandIssuedEvent event) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("schemaVersion", SCHEMA_VERSION);
        message.put("commandId", event.commandId());
        message.put("type", RemoteCommandType.BOOST.name());
        message.put("params", event.params());
        message.put("issuedAt", event.issuedAt());
        message.put("expiresAt", event.expiresAt());
        return message;
    }
}
