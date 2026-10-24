package vent.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vent.support.MqttIntegrationTest;

/**
 * 원격 명령(부스트) 통합 테스트.
 *
 * <p>테스트 지점: 부스트 API와 실제 브로커. 테스트용 게이트웨이 클라이언트가 command 토픽을 구독해 서버가 보낸 명령을 받고,
 * 적용·거절 응답을 보낸 뒤 명령 API로 상태를 확인한다. 게이트웨이 연결 상태가 다른 테스트와 섞이지 않도록 전용 현장 두 곳
 * (연결됨 92번, 일시 끊김 93번)을 쓰고, 테스트마다 연결 상태를 DB에 직접 맞춘다. 연결 상태가 바뀌는 과정은 #6 테스트가 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
class RemoteCommandApiTest extends MqttIntegrationTest {

    private static final String CONNECTED_VENTILATOR = "vent-t92";
    private static final String INTERRUPTED_VENTILATOR = "vent-t93";

    @Autowired
    private ObjectMapper objectMapper;

    private MqttClient deviceSide;
    private final List<MqttMessage> commandsReceived = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUpSitesAndDevice() throws Exception {
        registerTestSite(92, "gw-t92");
        registerTestDevice(92, "gw-t92", CONNECTED_VENTILATOR, "VENTILATOR", "DOMEKT R 300V");
        registerTestSite(93, "gw-t93");
        registerTestDevice(93, "gw-t93", INTERRUPTED_VENTILATOR, "VENTILATOR", "DOMEKT R 300V");

        // 둘 다 BOOST를 지원한다고 보고했다. 92번은 연결됨, 93번은 일시 끊김.
        jdbcTemplate.update("""
                UPDATE gateway SET connection_state = 'CONNECTED', last_received_at = now(), disconnected_at = NULL,
                       capabilities = '["BOOST", "HOOD_EVENT"]'::jsonb WHERE id = 'gw-t92'
                """);
        jdbcTemplate.update("""
                UPDATE gateway SET connection_state = 'INTERRUPTED', last_received_at = now(), disconnected_at = now(),
                       capabilities = '["BOOST", "HOOD_EVENT"]'::jsonb WHERE id = 'gw-t93'
                """);

        deviceSide = new MqttClient(brokerUri(), "test-device-side-" + UUID.randomUUID(), new MemoryPersistence());
        deviceSide.setCallback(new CommandInbox());
        deviceSide.connect(new MqttConnectionOptions());
        deviceSide.subscribe("jabjaje/+/+/command", 1);
    }

    @AfterEach
    void disconnectDeviceSide() throws MqttException {
        deviceSide.disconnect();
        deviceSide.close();
    }

    @Test
    @DisplayName("부스트를 요청하면 QoS 1, retained 없이 급기·배기가 함께 오른 명령이 가고, 장비가 적용하면 적용됨이 된다")
    void boostIsSentAndApplied() throws Exception {
        String commandId = requestBoost(CONNECTED_VENTILATOR, 273, 1800, "SENT");

        MqttMessage received = awaitCommand(commandId);
        assertThat(received.getQos()).isEqualTo(1);
        assertThat(received.isRetained()).isFalse();
        JsonNode command = objectMapper.readTree(received.getPayload());
        assertThat(command.path("type").asText()).isEqualTo("BOOST");
        assertThat(command.path("params").path("supplyCmh").asInt()).isEqualTo(273);
        assertThat(command.path("params").path("exhaustCmh").asInt()).isEqualTo(273);
        assertThat(command.path("params").path("durationSec").asInt()).isEqualTo(1800);
        assertThat(command.path("expiresAt").asText()).isNotEmpty();

        publishAck(CONNECTED_VENTILATOR, """
                {"schemaVersion": 1, "commandId": "%s", "result": "APPLIED", "appliedAt": "%s"}
                """.formatted(commandId, now()));
        awaitStatus(CONNECTED_VENTILATOR, commandId, "APPLIED");
    }

    @Test
    @DisplayName("장비가 동결 방지로 거절하면 거절됨과 사유가 남는다")
    void boostRejectedByDeviceKeepsReason() throws Exception {
        String commandId = requestBoost(CONNECTED_VENTILATOR, 250, 600, "SENT");
        awaitCommand(commandId);

        publishAck(CONNECTED_VENTILATOR, """
                {"schemaVersion": 1, "commandId": "%s", "result": "REJECTED", "reason": "FROST_PROTECTION"}
                """.formatted(commandId));
        awaitStatus(CONNECTED_VENTILATOR, commandId, "REJECTED");
        mockMvc.perform(get("/api/devices/" + CONNECTED_VENTILATOR + "/commands"))
                .andExpect(jsonPath("$[?(@.commandId == '%s')].rejectReason".formatted(commandId)).value("FROST_PROTECTION"));
    }

    @Test
    @DisplayName("게이트웨이가 연결됨이 아니면 보내지 않고 REJECTED_OFFLINE과 안내 문구를 남긴다")
    void notConnectedGatewayIsRejectedOffline() throws Exception {
        String commandId = requestBoost(INTERRUPTED_VENTILATOR, 250, 600, "REJECTED_OFFLINE");
        mockMvc.perform(get("/api/devices/" + INTERRUPTED_VENTILATOR + "/commands"))
                .andExpect(jsonPath("$[?(@.commandId == '%s')].notice".formatted(commandId))
                        .value("지금은 게이트웨이가 연결되지 않아 명령을 보낼 수 없어요"));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
                .until(() -> commandIdsReceived().stream().noneMatch(commandId::equals));
    }

    @Test
    @DisplayName("모델 최대 풍량을 넘는 요청은 기록하지 않고 400과 안내 문구를 돌려준다")
    void invalidBoostIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/devices/" + CONNECTED_VENTILATOR + "/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplyCmh\": 301, \"durationSec\": 600}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("최대 풍량")));
        Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM command WHERE device_id = ? AND params->>'supplyCmh' = '301'",
                Integer.class, CONNECTED_VENTILATOR);
        assertThat(stored).isZero();
    }

    @Test
    @DisplayName("다른 장비 토픽으로 온 응답과 이미 끝난 명령의 중복 응답은 무시한다")
    void foreignAndDuplicateAcksAreIgnored() throws Exception {
        String commandId = requestBoost(CONNECTED_VENTILATOR, 250, 600, "SENT");
        awaitCommand(commandId);

        // 다른 현장의 다른 장비가 이 명령에 응답하는 척한다 → 무시, 여전히 보냄
        publishAck(INTERRUPTED_VENTILATOR, """
                {"schemaVersion": 1, "commandId": "%s", "result": "APPLIED"}
                """.formatted(commandId));
        // 진짜 장비의 적용 응답 → 적용됨
        publishAck(CONNECTED_VENTILATOR, """
                {"schemaVersion": 1, "commandId": "%s", "result": "APPLIED", "appliedAt": "%s"}
                """.formatted(commandId, now()));
        awaitStatus(CONNECTED_VENTILATOR, commandId, "APPLIED");

        // 같은 명령에 거절 응답이 또 와도(중복·늦은 응답) 적용됨을 유지한다
        publishAck(CONNECTED_VENTILATOR, """
                {"schemaVersion": 1, "commandId": "%s", "result": "REJECTED", "reason": "FROST_PROTECTION"}
                """.formatted(commandId));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                mockMvc.perform(get("/api/devices/" + CONNECTED_VENTILATOR + "/commands"))
                        .andExpect(jsonPath("$[?(@.commandId == '%s')].status".formatted(commandId)).value("APPLIED")));
    }

    private String requestBoost(String deviceId, int supplyCmh, int durationSec, String expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/devices/" + deviceId + "/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplyCmh\": %d, \"durationSec\": %d}".formatted(supplyCmh, durationSec)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("commandId").asText();
    }

    private MqttMessage awaitCommand(String commandId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> commandIdsReceived().contains(commandId));
        return commandsReceived.stream()
                .filter(message -> commandId.equals(commandIdOf(message)))
                .findFirst().orElseThrow();
    }

    private List<String> commandIdsReceived() {
        return commandsReceived.stream().map(this::commandIdOf).toList();
    }

    private String commandIdOf(MqttMessage message) {
        try {
            return objectMapper.readTree(message.getPayload()).path("commandId").asText();
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private void publishAck(String deviceId, String json) throws MqttException {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS_AT_LEAST_ONCE);
        String siteId = deviceId.equals(CONNECTED_VENTILATOR) ? "92" : "93";
        deviceSide.publish("jabjaje/" + siteId + "/" + deviceId + "/command/ack", message);
    }

    private void awaitStatus(String deviceId, String commandId, String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/devices/" + deviceId + "/commands"))
                .andExpect(jsonPath("$[?(@.commandId == '%s')].status".formatted(commandId)).value(status)));
    }

    /**
     * 테스트용 장비 쪽 콜백. 받은 원격 명령을 모아 둔다.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#8)
     */
    private final class CommandInbox implements MqttCallback {

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            commandsReceived.add(message);
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
