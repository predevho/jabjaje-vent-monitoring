package vent.anomaly;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import vent.support.MqttIntegrationTest;

/**
 * CO2 과다 이상 통합 테스트.
 *
 * <p>테스트 지점: 실제 브로커로 측정값을 발행하고 이상 API로 확인한다. 지속 시간을 1분으로 줄이고, 측정 시각을
 * 30초 간격으로 "지금 + 10초"부터 앞으로 나눠 보낸다. 늦은 측정값(2분)과 시계 오차(5분) 기준에 걸리지 않는 범위라
 * 실제로 기다리지 않아도 지속을 만들 수 있다. 다른 테스트의 측정값과 섞이지 않도록 이 테스트 전용 장비를 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
@TestPropertySource(properties = "vent.anomaly.co2-high.lasting=1m")
class Co2HighAnomalyApiTest extends MqttIntegrationTest {

    private static final String CO2_HIGH_OF = "$[?(@.rule == 'CO2_HIGH' && @.deviceId == '%s')]";

    /** 시드 장비에는 다른 테스트가 남긴 측정값이 있어, 2번 현장에 이 테스트 전용 환기장치를 둔다. */
    private static final String DEVICE = "vent-s2-co2-test";

    @BeforeEach
    void registerDevice() {
        registerTestVentilator(2, "gw-s2", DEVICE);
    }

    @Test
    @DisplayName("1,000ppm 초과가 이어지면 주의로 열리고, 1,500ppm을 넘으면 심각으로 올라가고, 900ppm 미만이 이어지면 해결된다")
    void co2HighOpensEscalatesAndResolves() throws Exception {
        OffsetDateTime start = now().plusSeconds(10);

        // +10초, +40초, +70초: 1분 동안 1,100ppm → 주의
        for (int i = 0; i < 3; i++) {
            publishCo2(start.plusSeconds(30L * i), 1100);
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE), hasSize(1)))
                .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE) + ".severity", contains("WARN"))));

        // +100초: 1,600ppm → 같은 이상이 심각으로 올라간다(새로 생기지 않는다)
        publishCo2(start.plusSeconds(90), 1600);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE), hasSize(1)))
                .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE) + ".severity", contains("CRITICAL"))));

        // +130초, +160초, +190초: 1분 동안 800ppm → 해결
        for (int i = 0; i < 3; i++) {
            publishCo2(start.plusSeconds(120 + 30L * i), 800);
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mockMvc.perform(get("/api/anomalies?status=OPEN"))
                    .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE), empty()));
            mockMvc.perform(get("/api/anomalies?status=RESOLVED"))
                    .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE), hasSize(1)))
                    .andExpect(jsonPath(CO2_HIGH_OF.formatted(DEVICE) + ".severity", contains("CRITICAL")));
        });
    }

    /** 측정값 하나를 보내고 저장될 때까지 기다린다. 순서대로 평가되게 하기 위해서다. */
    private void publishCo2(OffsetDateTime measuredAt, int co2Ppm) throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish("2", DEVICE, telemetryJson(messageId, DEVICE, measuredAt, co2Ppm, ""));
        awaitStored(DEVICE, messageId);
    }
}
