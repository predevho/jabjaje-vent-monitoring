package vent.global.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MQTT 브로커 연결 설정 ({@code vent.mqtt.*}).
 *
 * <p>Paho의 {@code MqttProperties}(MQTT 5 패킷 속성)와 이름이 겹치지 않도록 Broker를 붙였다.
 *
 * @param serverUri 브로커 주소 (예: {@code tcp://localhost:1883})
 * @param clientId  서버의 MQTT 클라이언트 ID. 세션 유지를 위해 재시작해도 같은 값을 쓴다.
 *                  같은 ID로 두 곳이 접속하면 브로커가 먼저 붙은 쪽을 끊으므로, 서버 인스턴스마다 달라야 한다.
 * @param enabled   MQTT 연결을 켤지. MQTT가 필요 없는 테스트에서 끈다 (기본 true)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@ConfigurationProperties(prefix = "vent.mqtt")
public record MqttBrokerProperties(String serverUri, String clientId, @DefaultValue("true") boolean enabled) {
}
