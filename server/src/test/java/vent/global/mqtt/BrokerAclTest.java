package vent.global.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * 브로커 계정과 ACL 테스트 (스펙 9.2 S1, #13).
 *
 * <p>테스트 지점: 저장소의 실제 브로커 설정({@code infra/mosquitto}의 설정, ACL, 시작 스크립트)으로 띄운 Mosquitto.
 * Spring 없이 테스트용 MQTT 클라이언트로 접속해, 익명 거부와 현장별 토픽 권한을 확인한다. 비밀번호는 시작 스크립트의
 * 시연 기본값이다. 기능 통합 테스트들은 계정에 묶이지 않도록 익명 브로커를 그대로 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#13)
 */
@Testcontainers
class BrokerAclTest {

    private static final Path BROKER_CONFIG = Path.of("../infra/mosquitto");
    /** 다른 현장 토픽으로 보낸 메시지가 오지 않는 것을 이만큼 지켜본다 */
    private static final Duration NOT_DELIVERED_WATCH = Duration.ofSeconds(3);

    @Container
    static final GenericContainer<?> broker = new GenericContainer<>("eclipse-mosquitto:2")
            .withCopyFileToContainer(MountableFile.forHostPath(BROKER_CONFIG.resolve("mosquitto.conf")),
                    "/mosquitto/config/mosquitto.conf")
            .withCopyFileToContainer(MountableFile.forHostPath(BROKER_CONFIG.resolve("acl")), "/mosquitto/config/acl")
            .withCopyFileToContainer(MountableFile.forHostPath(BROKER_CONFIG.resolve("entrypoint.sh")),
                    "/mosquitto/config/entrypoint.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh", "/mosquitto/config/entrypoint.sh"))
            .withExposedPorts(1883);

    private final List<MqttClient> clients = new CopyOnWriteArrayList<>();

    @AfterEach
    void disconnectAll() {
        for (MqttClient client : clients) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (MqttException ignored) {
                // 테스트 정리 중 오류는 무시한다.
            }
        }
    }

    @Test
    @DisplayName("익명 접속과 틀린 비밀번호는 거부된다")
    void rejectsAnonymousAndWrongPassword() {
        // MQTT 5 CONNACK 이유 코드: 0x86(134) 계정·비밀번호 틀림, 0x87(135) 권한 없음
        assertThatThrownBy(() -> connect(null, null, new Received()))
                .isInstanceOfSatisfying(MqttException.class, e -> assertThat(e.getReasonCode()).isIn(134, 135));
        assertThatThrownBy(() -> connect("gw-s1", "wrong-password", new Received()))
                .isInstanceOfSatisfying(MqttException.class, e -> assertThat(e.getReasonCode()).isIn(134, 135));
    }

    @Test
    @DisplayName("현장 1 게이트웨이가 자기 현장 토픽에 보낸 측정값은 서버가 받고, 현장 2 토픽에 보낸 것은 받지 못한다")
    void gatewayCanPublishOnlyToOwnSite() throws Exception {
        Received server = new Received();
        MqttClient serverClient = connect("vent-server", "vent-server-demo", server);
        serverClient.subscribe("jabjaje/+/+/telemetry", 1);
        MqttClient gateway = connect("gw-s1", "gw-s1-demo", new Received());

        String own = "own-" + UUID.randomUUID();
        gateway.publish("jabjaje/1/vent-s1/telemetry", message(own));
        await().atMost(Duration.ofSeconds(5)).until(() -> server.contains(own));

        String foreign = "foreign-" + UUID.randomUUID();
        publishIgnoringRefusal(gateway, "jabjaje/2/vent-s2/telemetry", foreign);
        // 같은 연결로 허용된 메시지를 하나 더 보내 연결이 살아 있는 것을 확인한다. 끊겨서 안 온 것과 구분하기 위해서다.
        String canary = "canary-" + UUID.randomUUID();
        gateway.publish("jabjaje/1/vent-s1/telemetry", message(canary));
        await().atMost(Duration.ofSeconds(5)).until(() -> server.contains(canary));
        await().during(NOT_DELIVERED_WATCH).atMost(NOT_DELIVERED_WATCH.plusSeconds(2))
                .until(() -> !server.contains(foreign));
    }

    @Test
    @DisplayName("게이트웨이는 자기 현장 원격 명령만 받고, 다른 현장 원격 명령은 구독해도 받지 못한다")
    void gatewayReceivesOnlyOwnSiteCommands() throws Exception {
        Received gatewayInbox = new Received();
        MqttClient gateway = connect("gw-s1", "gw-s1-demo", gatewayInbox);
        gateway.subscribe("jabjaje/1/+/command", 1);
        subscribeIgnoringRefusal(gateway, "jabjaje/2/+/command");
        MqttClient server = connect("vent-server", "vent-server-demo", new Received());

        String own = "own-" + UUID.randomUUID();
        server.publish("jabjaje/1/vent-s1/command", message(own));
        await().atMost(Duration.ofSeconds(5)).until(() -> gatewayInbox.contains(own));

        String foreign = "foreign-" + UUID.randomUUID();
        server.publish("jabjaje/2/vent-s2/command", message(foreign));
        String canary = "canary-" + UUID.randomUUID();
        server.publish("jabjaje/1/vent-s1/command", message(canary));
        await().atMost(Duration.ofSeconds(5)).until(() -> gatewayInbox.contains(canary));
        await().during(NOT_DELIVERED_WATCH).atMost(NOT_DELIVERED_WATCH.plusSeconds(2))
                .until(() -> !gatewayInbox.contains(foreign));
    }

    @Test
    @DisplayName("게이트웨이는 자기 현장 원격 명령 토픽에도 발행할 수 없다: 원격 명령은 서버만 보낸다")
    void gatewayCannotSendCommands() throws Exception {
        // 계정 이름이 클라이언트 ID라 같은 계정으로 두 번 접속하면 앞 연결이 끊긴다. 그래서 한 연결로 구독과 발행을 함께 한다.
        // 허용됐다면 MQTT는 자기가 보낸 메시지도 구독자에게 돌려준다.
        Received inbox = new Received();
        MqttClient gateway = connect("gw-s1", "gw-s1-demo", inbox);
        gateway.subscribe("jabjaje/1/+/command", 1);

        String spoofed = "spoofed-" + UUID.randomUUID();
        publishIgnoringRefusal(gateway, "jabjaje/1/vent-s1/command", spoofed);
        // 서버가 보낸 원격 명령은 받는다: 연결이 살아 있고 구독도 된 상태에서 위조만 막혔다.
        MqttClient server = connect("vent-server", "vent-server-demo", new Received());
        String canary = "canary-" + UUID.randomUUID();
        server.publish("jabjaje/1/vent-s1/command", message(canary));
        await().atMost(Duration.ofSeconds(5)).until(() -> inbox.contains(canary));
        await().during(NOT_DELIVERED_WATCH).atMost(NOT_DELIVERED_WATCH.plusSeconds(2))
                .until(() -> !inbox.contains(spoofed));
    }

    private MqttClient connect(String username, String password, Received inbox) throws MqttException {
        String uri = "tcp://" + broker.getHost() + ":" + broker.getMappedPort(1883);
        MqttClient client = new MqttClient(uri, "acl-test-" + UUID.randomUUID(), new MemoryPersistence());
        client.setCallback(inbox);
        MqttConnectionOptions options = new MqttConnectionOptions();
        if (username != null) {
            options.setUserName(username);
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        }
        clients.add(client);
        client.connect(options);
        return client;
    }

    private static MqttMessage message(String body) {
        MqttMessage message = new MqttMessage(body.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        return message;
    }

    /** 브로커는 권한 없는 발행을 거부 응답으로 돌려줄 수 있다. 여기서는 "전달되지 않는다"만 확인한다. */
    private static void publishIgnoringRefusal(MqttClient client, String topic, String body) {
        try {
            client.publish(topic, message(body));
        } catch (MqttException refused) {
            // 거부 응답(Not authorized). 전달되지 않았는지는 받는 쪽에서 확인한다.
        }
    }

    private static void subscribeIgnoringRefusal(MqttClient client, String topicFilter) {
        try {
            client.subscribe(topicFilter, 1);
        } catch (MqttException refused) {
            // 거부 응답(Not authorized). 받지 못하는지는 받는 쪽에서 확인한다.
        }
    }

    /**
     * 받은 메시지 본문을 모아 두는 테스트용 콜백.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#13)
     */
    private static final class Received implements MqttCallback {

        private final List<String> bodies = new CopyOnWriteArrayList<>();

        boolean contains(String body) {
            return bodies.contains(body);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            bodies.add(new String(message.getPayload(), StandardCharsets.UTF_8));
        }

        @Override
        public void disconnected(MqttDisconnectResponse response) {
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
        }
    }
}
