package vent.telemetry;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vent.global.mqtt.MqttClientManager;

/**
 * 측정값 수신 통합 테스트.
 *
 * <p>테스트 지점: 실제 Mosquitto 브로커로 측정값을 발행하고, 실제 PostgreSQL에 저장된 결과를 조회 API로 확인한다.
 * 테스트마다 다른 장비나 다른 messageId를 써서 실행 순서와 관계없이 결과가 같다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("demo")
class TelemetryIngestApiTest {

    private static final int QOS_AT_LEAST_ONCE = 1;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .withExposedPorts(1883);

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("vent.mqtt.server-uri", TelemetryIngestApiTest::brokerUri);
    }

    static MqttClient gateway;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MqttClientManager mqttClientManager;

    /** 서버가 구독을 마치기 전에 보낸 메시지는 사라지므로, 구독이 끝날 때까지 기다린 뒤 보낸다. */
    @BeforeEach
    void waitUntilServerSubscribed() {
        await().atMost(Duration.ofSeconds(15)).until(mqttClientManager::isReady);
    }

    @BeforeAll
    static void connectTestGateway() throws Exception {
        gateway = new MqttClient(brokerUri(), "test-gateway", new MemoryPersistence());
        gateway.connect(new MqttConnectionOptions());
    }

    @AfterAll
    static void disconnectTestGateway() throws Exception {
        gateway.disconnect();
        gateway.close();
    }

    @Test
    @DisplayName("측정값을 보내면 장비의 최신 측정값으로 조회되고, 측정 시각과 수신 시각이 모두 있다")
    void storedTelemetryAppearsAsLatest() throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish("3", "vent-s3", telemetryJson(messageId, "vent-s3", ""));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/telemetry/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].messageId", contains(messageId)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].co2Ppm", contains(820)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].supplyCmh", contains(205)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].filterPa", contains(85)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].bypass", contains(false)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].measuredAt", contains("2026-10-24T01:00:00Z")))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].receivedAt").isNotEmpty()));
    }

    @Test
    @DisplayName("같은 messageId를 두 번 보내면 한 건만 저장된다")
    void duplicateMessageIsStoredOnce() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String sentinelId = UUID.randomUUID().toString();
        publish("2", "vent-s2", telemetryJson(messageId, "vent-s2", ""));
        publish("2", "vent-s2", telemetryJson(messageId, "vent-s2", ""));
        publish("2", "vent-s2", telemetryJson(sentinelId, "vent-s2", ""));

        // 같은 구독으로 순서대로 처리되므로, 마지막에 보낸 메시지가 보이면 앞의 두 메시지도 처리가 끝난 것이다.
        awaitStored("vent-s2", sentinelId);
        mockMvc.perform(get("/api/devices/vent-s2/telemetry"))
                .andExpect(jsonPath("$[?(@.messageId == '" + messageId + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("토픽의 장비 ID와 메시지의 장비 ID가 다르면 저장되지 않는다")
    void deviceIdMismatchIsRejected() throws Exception {
        String spoofedId = UUID.randomUUID().toString();
        String sentinelId = UUID.randomUUID().toString();
        publish("1", "vent-s1", telemetryJson(spoofedId, "vent-s2", ""));
        publish("1", "vent-s1", telemetryJson(sentinelId, "vent-s1", ""));

        awaitStored("vent-s1", sentinelId);
        mockMvc.perform(get("/api/devices/vent-s1/telemetry"))
                .andExpect(jsonPath("$[*].messageId", not(hasItem(spoofedId))));
        mockMvc.perform(get("/api/devices/vent-s2/telemetry"))
                .andExpect(jsonPath("$[*].messageId", not(hasItem(spoofedId))));
    }

    @Test
    @DisplayName("모르는 필드가 있어도 측정값은 저장된다")
    void unknownFieldsAreIgnored() throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish("1", "vent-s1", telemetryJson(messageId, "vent-s1", "\"pm25\": 12, "));

        awaitStored("vent-s1", messageId);
    }

    private void awaitStored(String deviceId, String messageId) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/api/devices/" + deviceId + "/telemetry"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[*].messageId", hasItem(messageId))));
    }

    private static void publish(String siteId, String deviceId, String json) throws Exception {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS_AT_LEAST_ONCE);
        gateway.publish("jabjaje/" + siteId + "/" + deviceId + "/telemetry", message);
    }

    /** 스펙 4.2의 측정값 예시와 같은 값. extraFields는 앞에 끼워 넣을 추가 필드다. */
    private static String telemetryJson(String messageId, String deviceId, String extraFields) {
        return """
                {%s"schemaVersion": 1, "messageId": "%s", "deviceId": "%s",
                 "measuredAt": "2026-10-24T10:00:00+09:00",
                 "co2Ppm": 820, "tempC": 23.4, "humidityPct": 48,
                 "supplyCmh": 205, "exhaustCmh": 200, "filterPa": 85, "bypass": false}
                """.formatted(extraFields, messageId, deviceId);
    }

    private static String brokerUri() {
        return "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
    }
}
