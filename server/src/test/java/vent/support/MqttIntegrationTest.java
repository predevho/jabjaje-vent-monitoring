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
import org.springframework.jdbc.core.JdbcTemplate;
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
 * 측정값(#2, #3), 이상(#4, #5), 게이트웨이 연결 상태(#6), 후드 연동(#7) 테스트가 함께 쓴다.
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
        // 무수신 검사 스케줄러는 끈다. 테스트가 검사 시각을 직접 넣어 부른다(#6).
        registry.add("vent.gateway.connection.check-enabled", () -> "false");
        // 후드 연동 판정 스케줄러도 끈다. 테스트가 판정 시각을 직접 넣어 부른다(#7).
        registry.add("vent.hood.interlock.check-enabled", () -> "false");
    }

    protected static MqttClient gateway;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MqttClientManager mqttClientManager;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

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

    /** 게이트웨이 상태 토픽에 보낸다(#6). 테스트끼리 남지 않도록 retained로 보내지 않는다. */
    protected static void publishStatus(String siteId, String gatewayId, String json) throws Exception {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS_AT_LEAST_ONCE);
        gateway.publish("jabjaje/" + siteId + "/" + gatewayId + "/status", message);
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

    /**
     * 테스트 전용 환기장치를 시드 현장에 등록한다(이미 있으면 그대로). 다른 테스트 클래스가 시드 장비에 남긴 측정값과
     * 섞이면 안 되는 "N분 지속" 테스트가 쓴다(#5).
     */
    protected void registerTestVentilator(long siteId, String gatewayId, String deviceId) {
        registerTestDevice(siteId, gatewayId, deviceId, "VENTILATOR", "DOMEKT R 300V");
    }

    /**
     * 테스트 전용 현장과 그 게이트웨이(연결 전)를 등록한다(이미 있으면 그대로). 다른 테스트의 측정값·이벤트·연결 상태와
     * 섞이면 안 되는 테스트(#7, #8)가 쓴다. 시드 현장(1~3)과 겹치지 않는 번호를 쓴다.
     */
    protected void registerTestSite(long siteId, String gatewayId) {
        jdbcTemplate.update("""
                INSERT INTO site (id, name, region, floor_area_m2, occupants, target_airflow_cmh, target_airflow_source)
                VALUES (?, ?, '테스트', 100.0, 3, 150, 'MINIMUM_AIRFLOW')
                ON CONFLICT (id) DO NOTHING
                """, siteId, "테스트 현장 " + siteId);
        jdbcTemplate.update("INSERT INTO gateway (id, site_id, connection_state) VALUES (?, ?, 'PENDING') "
                + "ON CONFLICT (id) DO NOTHING", gatewayId, siteId);
    }

    /** 테스트 전용 장비를 현장에 등록한다(이미 있으면 그대로). 후드 연동 테스트(#7)는 VEMC를 등록한다. */
    protected void registerTestDevice(long siteId, String gatewayId, String deviceId, String type, String model) {
        jdbcTemplate.update("""
                INSERT INTO device (id, site_id, gateway_id, type, model)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, deviceId, siteId, gatewayId, type, model);
    }

    /** 장비 이벤트 토픽에 보낸다(#7). */
    protected static void publishEvent(String siteId, String deviceId, String json) throws Exception {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS_AT_LEAST_ONCE);
        gateway.publish("jabjaje/" + siteId + "/" + deviceId + "/event", message);
    }

    /** 이 테스트가 넣은 측정값을 지운다. 미래 시각 측정값이 남으면 뒤 테스트의 "최신 측정값"이 바뀐다(#5). */
    protected void deleteTelemetry(String... messageIds) {
        for (String messageId : messageIds) {
            jdbcTemplate.update("DELETE FROM telemetry WHERE message_id = ?::uuid", messageId);
        }
    }

    /** 초 단위로 자른 지금 시각(UTC). 응답과 문자열로 비교하기 쉽게 자른다. */
    protected static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    protected static String brokerUri() {
        return "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
    }
}
