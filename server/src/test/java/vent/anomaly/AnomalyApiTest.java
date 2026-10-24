package vent.anomaly;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.repository.AnomalyRepository;
import vent.support.MqttIntegrationTest;

/**
 * 이상 감지 통합 테스트 (필터 막힘).
 *
 * <p>테스트 지점: 실제 브로커로 측정값을 발행하고, 이상 API로 열린·해제된 이상을 확인한다.
 * 기준은 application.yml 기본값(모든 모델 150Pa, 해제 여유 20Pa)이다. 테스트마다 다른 장비를 써서 서로 영향을 주지 않는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
class AnomalyApiTest extends MqttIntegrationTest {

    /** 응답 목록에서 그 장비의 필터 막힘 이상만 고르는 JSONPath. 열린 목록과 해결된 목록 모두에 쓴다. */
    private static final String FILTER_CLOGGED_OF_DEVICE = "$[?(@.rule == 'FILTER_CLOGGED' && @.deviceId == '%s')]";

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Test
    @DisplayName("필터 차압이 기준을 넘으면 이상이 한 번만 열리고, 필터를 갈면 해제된다")
    void filterCloggedOpensOnceAndResolves() throws Exception {
        publish("2", "vent-s2", telemetryJson(UUID.randomUUID().toString(), "vent-s2", now(), 820, 200, ""));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2"), hasSize(1)))
                .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2") + ".severity", contains("WARN")))
                .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2") + ".siteName", contains("안성 현장"))));

        // 계속 막혀 있어도 이상은 하나뿐이다 (중복 방지).
        publish("2", "vent-s2", telemetryJson(UUID.randomUUID().toString(), "vent-s2", now().plusSeconds(1), 820, 210, ""));
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/anomalies?status=OPEN"))
                        .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2"), hasSize(1))));

        // 필터를 갈면 차압이 해제 기준(130Pa) 아래로 내려가 해제된다.
        publish("2", "vent-s2", telemetryJson(UUID.randomUUID().toString(), "vent-s2", now().plusSeconds(2), 820, 60, ""));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mockMvc.perform(get("/api/anomalies?status=OPEN"))
                    .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2"), empty()));
            mockMvc.perform(get("/api/anomalies?status=RESOLVED"))
                    .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2"), hasSize(1)))
                    .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s2") + ".resolvedAt").isNotEmpty());
        });
    }

    @Test
    @DisplayName("품질이 OK가 아닌 측정값(범위 밖)으로는 판단하지 않는다")
    void outOfRangeTelemetryDoesNotRaise() throws Exception {
        String okId = UUID.randomUUID().toString();
        String faultyId = UUID.randomUUID().toString();
        publish("3", "vent-s3", telemetryJson(okId, "vent-s3", now(), 820, 80, ""));
        awaitStored("vent-s3", okId);
        // CO2 0ppm이라 범위 밖. 필터 차압 300Pa는 믿을 수 없는 값이라 판단에 쓰지 않는다.
        publish("3", "vent-s3", telemetryJson(faultyId, "vent-s3", now().plusSeconds(1), 0, 300, ""));
        awaitStored("vent-s3", faultyId);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/anomalies?status=OPEN"))
                        .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s3"), empty())));
    }

    @Test
    @DisplayName("측정 시각이 더 이른 늦은 측정값은 최신 판단을 뒤집지 않는다")
    void lateOlderTelemetryDoesNotResolve() throws Exception {
        publish("1", "vent-s1", telemetryJson(UUID.randomUUID().toString(), "vent-s1", now(), 820, 200, ""));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s1"), hasSize(1))));

        // 10분 전에 잰 값(필터 교체 전)이 늦게 도착해도, 측정 시각 기준 최신 값은 여전히 200Pa다.
        String lateId = UUID.randomUUID().toString();
        publish("1", "vent-s1", telemetryJson(lateId, "vent-s1", now().minusMinutes(10), 820, 60, ""));
        awaitStored("vent-s1", lateId);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/anomalies?status=OPEN"))
                        .andExpect(jsonPath(FILTER_CLOGGED_OF_DEVICE.formatted("vent-s1"), hasSize(1))));
    }

    @Test
    @DisplayName("같은 장비·규칙의 열린 이상을 두 번 저장하면 DB가 거부한다 (서비스 중복 방지의 안전망)")
    void databaseRejectsSecondOpenAnomalyForSameDeviceAndRule() {
        // 측정값을 보내지 않는 VEMC 장비를 써서 다른 테스트와 겹치지 않게 한다.
        Anomaly first = anomalyRepository.save(Anomaly.openForDevice(
                "vemc-s3", AnomalyRuleName.FILTER_CLOGGED, AnomalySeverity.WARN, "첫 번째", now()));
        try {
            assertThatThrownBy(() -> anomalyRepository.save(Anomaly.openForDevice(
                    "vemc-s3", AnomalyRuleName.FILTER_CLOGGED, AnomalySeverity.WARN, "두 번째", now())))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            anomalyRepository.delete(first);
        }
    }
}
