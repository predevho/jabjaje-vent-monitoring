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
 * 원격 명령 발행 (스펙 4.1, 4.2, #8).
 *
 * <p>{@code jabjaje/{현장ID}/{장비ID}/command}에 QoS 1, retained 없이 보낸다. 메시지에는 만료 시각(발행 + 60초)을 넣는다.
 * 장비가 만료를 검사하고 MQTT 5 메시지 만료를 거는 것은 #9에서 한다. 발행이 실패하면 명령은 보냄(SENT)으로 남고,
 * #9의 재시도와 시간 초과가 처리한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Service
public class RemoteCommandSender {

    private static final Logger log = LoggerFactory.getLogger(RemoteCommandSender.class);
    private static final int SCHEMA_VERSION = 1;
    private static final Duration EXPIRES_AFTER = Duration.ofSeconds(60);

    private final MqttClientManager mqttClientManager;
    private final ObjectMapper objectMapper;

    public RemoteCommandSender(MqttClientManager mqttClientManager, ObjectMapper objectMapper) {
        this.mqttClientManager = mqttClientManager;
        this.objectMapper = objectMapper;
    }

    public void send(RemoteCommandIssuedEvent event) {
        String topic = "jabjaje/" + event.siteId() + "/" + event.deviceId() + "/command";
        try {
            mqttClientManager.publish(topic, objectMapper.writeValueAsBytes(message(event)));
            log.info("원격 명령 보냄: {} {} {}", event.deviceId(), RemoteCommandType.BOOST, event.commandId());
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
        // 만료는 발행 시각 기준이다 (스펙 7.1). 요청 시각과는 커밋 시간만큼 차이가 난다.
        message.put("expiresAt", OffsetDateTime.now(ZoneOffset.UTC).plus(EXPIRES_AFTER));
        return message;
    }
}
