package vent.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vent.gateway.domain.Gateway;
import vent.gateway.listener.GatewayStatusMqttListener;
import vent.gateway.repository.GatewayRepository;
import vent.gateway.service.GatewayConnectionService;
import vent.support.MqttIntegrationTest;

/**
 * 게이트웨이 연결 상태 통합 테스트.
 *
 * <p>테스트 지점: 실제 브로커로 상태 토픽·측정값을 발행하고 현장 API와 이상 API로 확인한다.
 * 무수신 검사는 스케줄러를 끄고(테스트 기반 설정) 검사 시각을 직접 넣어 부른다. 30분을 기다릴 수 없어서다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
class GatewayConnectionApiTest extends MqttIntegrationTest {

    private static final String ONLINE = """
            {"state": "online", "firmwareVersion": "1.0.0", "schemaVersion": 1, "capabilities": ["BOOST", "HOOD_EVENT"]}
            """;
    private static final String OFFLINE = """
            {"state": "offline", "schemaVersion": 1}
            """;
    /** 현장 API 응답에서 그 게이트웨이를 고르는 JSONPath */
    private static final String GATEWAY_OF = "$[?(@.gateway.id == '%s')].gateway";
    /** 이상 API 응답에서 그 게이트웨이의 오프라인 이상을 고르는 JSONPath */
    private static final String GATEWAY_OFFLINE_OF = "$[?(@.rule == 'GATEWAY_OFFLINE' && @.gatewayId == '%s')]";

    @Autowired
    private GatewayConnectionService connectionService;

    @Autowired
    private GatewayStatusMqttListener statusListener;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Test
    @DisplayName("연결됨 → 일시 끊김 → 오프라인(이상 열림) → 다시 수신하면 연결됨(이상 해결)")
    void connectedInterruptedOfflineConnected() throws Exception {
        publishStatus("1", "gw-s1", ONLINE);
        awaitGateway("gw-s1", "CONNECTED");
        mockMvc.perform(get("/api/sites"))
                .andExpect(jsonPath(GATEWAY_OF.formatted("gw-s1") + ".firmwareVersion", contains("1.0.0")));

        // Last Will: 브로커가 끊긴 게이트웨이 대신 offline을 보낸다.
        publishStatus("1", "gw-s1", OFFLINE);
        awaitGateway("gw-s1", "INTERRUPTED");
        mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(GATEWAY_OFFLINE_OF.formatted("gw-s1"), empty()));

        // 끊긴 지 31분이 지났다고 보고 검사한다.
        connectionService.checkSilence(now().plusMinutes(31));
        awaitGateway("gw-s1", "OFFLINE");
        mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(GATEWAY_OFFLINE_OF.formatted("gw-s1"), hasSize(1)))
                .andExpect(jsonPath(GATEWAY_OFFLINE_OF.formatted("gw-s1") + ".severity", contains("WARN")))
                .andExpect(jsonPath(GATEWAY_OFFLINE_OF.formatted("gw-s1") + ".siteName", contains("양평 현장")));

        // 다시 연결되어 측정값이 들어오면 연결됨, 이상은 해결된다.
        publish("1", "vent-s1", telemetryJson(UUID.randomUUID().toString(), "vent-s1", ""));
        awaitGateway("gw-s1", "CONNECTED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mockMvc.perform(get("/api/anomalies?status=OPEN"))
                    .andExpect(jsonPath(GATEWAY_OFFLINE_OF.formatted("gw-s1"), empty()));
            mockMvc.perform(get("/api/anomalies?status=RESOLVED"))
                    .andExpect(jsonPath(GATEWAY_OFFLINE_OF.formatted("gw-s1"), hasSize(1)));
        });
    }

    @Test
    @DisplayName("다시 구독할 때 브로커가 보내는 옛날 retained online은 연결 상태를 바꾸지 않고 버전만 기록한다")
    void retainedOnlineDoesNotChangeConnectionState() {
        Gateway before = gatewayRepository.findById("gw-s3").orElseThrow();

        statusListener.handle("jabjaje/3/gw-s3/status", ONLINE.getBytes(StandardCharsets.UTF_8), true);

        Gateway after = gatewayRepository.findById("gw-s3").orElseThrow();
        assertThat(after.getConnectionState()).isEqualTo(before.getConnectionState());
        assertThat(after.getLastReceivedAt()).isEqualTo(before.getLastReceivedAt());
        assertThat(after.getFirmwareVersion()).isEqualTo("1.0.0");
        assertThat(after.getCapabilities()).containsExactly("BOOST", "HOOD_EVENT");
    }

    @Test
    @DisplayName("다른 현장의 상태 토픽으로 온 보고는 무시한다")
    void statusOnWrongSiteTopicIsIgnored() throws Exception {
        Gateway before = gatewayRepository.findById("gw-s2").orElseThrow();

        // gw-s2는 2번 현장의 게이트웨이다.
        statusListener.handle("jabjaje/1/gw-s2/status", ONLINE.getBytes(StandardCharsets.UTF_8), false);

        Gateway after = gatewayRepository.findById("gw-s2").orElseThrow();
        assertThat(after.getConnectionState()).isEqualTo(before.getConnectionState());
        assertThat(after.getLastReceivedAt()).isEqualTo(before.getLastReceivedAt());
    }

    private void awaitGateway(String gatewayId, String state) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/sites"))
                .andExpect(jsonPath(GATEWAY_OF.formatted(gatewayId) + ".connectionState", contains(state))));
    }
}
