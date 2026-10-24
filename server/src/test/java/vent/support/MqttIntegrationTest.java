package vent.support;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import vent.global.mqtt.MqttClientManager;

/**
 * MQTT 통합 테스트 공통 기반.
 *
 * <p>실제 Mosquitto와 PostgreSQL 컨테이너를 띄우고, 테스트용 게이트웨이 클라이언트로 측정값을 발행하는 도우미를 준다.
 * 측정값(#2, #3)과 이상(#4) 테스트가 함께 쓴다.
 *
 * <ul>
 *   <li>컨테이너는 JVM에서 한 번만 띄워 모든 테스트 클래스가 함께 쓴다(singleton container). 클래스마다 다시 띄우면
 *       캐시된 Spring 컨텍스트가 사라진 컨테이너 주소를 들고 있게 된다.</li>
 *   <li>테스트 클래스가 끝나면 컨텍스트를 닫는다({@code @DirtiesContext}). 닫지 않으면 이전 클래스의 서버가 같은 브로커를
 *       계속 구독해서, 다음 클래스의 메시지를 대신 처리해 버린다.</li>
 * </ul>
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MqttIntegrationTest {

    protected static final int QOS_AT_LEAST_ONCE = 1;

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    protected static final GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .withExposedPorts(1883);

    static {
        postgres.start();
        mosquitto.start();
    }

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("vent.mqtt.server-uri", MqttIntegrationTest::brokerUri);
        // 로컬에서 실행 중인 시연 서버(vent-server)와 겹치지 않는 클라이언트 ID
        registry.add("vent.mqtt.client-id", () -> "vent-server-test-" + UUID.randomUUID());
    }

    protected static MqttClient gateway;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MqttClientManager mqttClientManager;

    @BeforeAll
    static void connectTestGateway() throws Exception {
        gateway = new MqttClient(brokerUri(), "test-gateway-" + UUID.randomUUID(), new MemoryPersistence());
        gateway.connect(new MqttConnectionOptions());
    }

    @AfterAll
    static void disconnectTestGateway() throws Exception {
        gateway.disconnect();
        gateway.close();
    }

    /** 서버가 구독을 마치기 전에 보낸 메시지는 사라지므로, 구독이 끝날 때까지 기다린 뒤 보낸다. */
    @BeforeEach
    void waitUntilServerSubscribed() {
        await().atMost(Duration.ofSeconds(15)).until(mqttClientManager::isReady);
    }

    /** 그 장비의 측정값 목록에 messageId가 보일 때까지 기다린다. */
    protected void awaitStored(String deviceId, String messageId) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/api/devices/" + deviceId + "/telemetry"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[*].messageId", hasItem(messageId))));
    }

    protected static void publish(String siteId, String deviceId, String json) throws Exception {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS_AT_LEAST_ONCE);
        gateway.publish("jabjaje/" + siteId + "/" + deviceId + "/telemetry", message);
    }

    /** 지금 측정한 정상 측정값. extraFields는 앞에 끼워 넣을 추가 필드다. */
    protected static String telemetryJson(String messageId, String deviceId, String extraFields) {
        return telemetryJson(messageId, deviceId, now(), 820, extraFields);
    }

    /** 스펙 4.2의 측정값 예시와 같은 값에 측정 시각과 CO2만 바꾼다. */
    protected static String telemetryJson(String messageId, String deviceId, OffsetDateTime measuredAt,
                                          int co2Ppm, String extraFields) {
        return telemetryJson(messageId, deviceId, measuredAt, co2Ppm, 85, extraFields);
    }

    /** 스펙 4.2의 측정값 예시와 같은 값에 측정 시각, CO2, 필터 차압을 바꾼다. */
    protected static String telemetryJson(String messageId, String deviceId, OffsetDateTime measuredAt,
                                          int co2Ppm, int filterPa, String extraFields) {
        return """
                {%s"schemaVersion": 1, "messageId": "%s", "deviceId": "%s",
                 "measuredAt": "%s",
                 "co2Ppm": %d, "tempC": 23.4, "humidityPct": 48,
                 "supplyCmh": 205, "exhaustCmh": 200, "filterPa": %d, "bypass": false}
                """.formatted(extraFields, messageId, deviceId, measuredAt, co2Ppm, filterPa);
    }

    /** 초 단위로 자른 지금 시각(UTC). 응답과 문자열로 비교하기 쉽게 자른다. */
    protected static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    protected static String brokerUri() {
        return "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
    }
}
