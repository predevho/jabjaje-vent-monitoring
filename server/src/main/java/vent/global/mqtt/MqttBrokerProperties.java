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
 *                  브로커가 계정 이름을 클라이언트 ID로 쓰므로(#13) 서버 인스턴스마다 계정도 따로 둔다.
 * @param enabled   MQTT 연결을 켤지. MQTT가 필요 없는 테스트에서 끈다 (기본 true)
 * @param username  브로커 계정 (#13). 비어 있으면 익명으로 접속한다(통합 테스트의 익명 브로커).
 *                  브로커는 계정 이름을 클라이언트 ID로 쓰므로 {@code clientId}와 같게 둔다
 * @param password  브로커 비밀번호 (#13). 환경변수로만 넣는다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@ConfigurationProperties(prefix = "vent.mqtt")
public record MqttBrokerProperties(String serverUri, String clientId, @DefaultValue("true") boolean enabled,
                                   String username, String password) {

    /**
     * 계정을 넣었으면 비밀번호도 있어야 하고, 클라이언트 ID는 계정 이름과 같아야 한다. 잘못 넣으면 브로커가 계속 거부하거나
     * 클라이언트 ID를 바꿔 세션이 이어지지 않으므로, 재시도만 반복하지 않게 시작할 때 멈춘다.
     */
    public MqttBrokerProperties {
        if (username != null && !username.isBlank()) {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("vent.mqtt.username을 넣었으면 vent.mqtt.password도 넣어야 합니다");
            }
            if (!username.equals(clientId)) {
                throw new IllegalArgumentException("브로커가 계정 이름을 클라이언트 ID로 쓰므로 vent.mqtt.client-id("
                        + clientId + ")를 vent.mqtt.username(" + username + ")과 같게 두어야 합니다");
            }
        }
    }

    /** 로그에 설정이 찍혀도 비밀번호가 드러나지 않게 가린다. */
    @Override
    public String toString() {
        return "MqttBrokerProperties[serverUri=" + serverUri + ", clientId=" + clientId + ", enabled=" + enabled
                + ", username=" + username + ", password=" + (password == null ? "null" : "****") + "]";
    }

    /** 계정으로 접속하는지. */
    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }
}
