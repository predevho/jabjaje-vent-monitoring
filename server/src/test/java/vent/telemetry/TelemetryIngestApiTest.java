package vent.telemetry;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vent.support.MqttIntegrationTest;
import vent.telemetry.dto.request.TelemetryMessage;
import vent.telemetry.service.TelemetryIngestService;

/**
 * 측정값 수신 통합 테스트.
 *
 * <p>테스트 지점: 실제 Mosquitto 브로커로 측정값을 발행하고, 실제 PostgreSQL에 저장된 결과를 조회 API로 확인한다.
 * 테스트마다 다른 장비나 다른 messageId를 써서 실행 순서와 관계없이 결과가 같다. 컨테이너와 발행 도우미는 {@link MqttIntegrationTest}에 있다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
class TelemetryIngestApiTest extends MqttIntegrationTest {

    /** DB 장애를 흉내 내려고 실제 서비스를 감싼다. 끼워 넣지 않은 호출은 실제 메서드로 간다. 테스트마다 초기화된다. */
    @MockitoSpyBean
    TelemetryIngestService ingestService;

    @Test
    @DisplayName("측정값을 보내면 장비의 최신 측정값으로 조회되고, 측정 시각과 수신 시각이 모두 있다")
    void storedTelemetryAppearsAsLatest() throws Exception {
        String messageId = UUID.randomUUID().toString();
        OffsetDateTime measuredAt = now();
        publish("3", "vent-s3", telemetryJson(messageId, "vent-s3", measuredAt, 820, ""));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/telemetry/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].messageId", contains(messageId)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].co2Ppm", contains(820)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].supplyCmh", contains(205)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].filterPa", contains(85)))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].bypass", contains(false)))
                // toString()은 0초를 생략해(06:13Z) JSON(06:13:00Z)과 달라지므로 ISO 형식으로 비교한다.
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].measuredAt",
                        contains(measuredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].receivedAt").isNotEmpty())
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].quality", contains("OK")))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s3')].late", contains(false))));
    }

    @Test
    @DisplayName("측정 후 2분 넘게 지나서 받은 측정값은 늦은 측정값으로 저장된다")
    void lateTelemetryIsMarkedLate() throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish("1", "vent-s1", telemetryJson(messageId, "vent-s1", now().minusMinutes(5), 820, ""));

        awaitStored("vent-s1", messageId);
        mockMvc.perform(get("/api/devices/vent-s1/telemetry"))
                .andExpect(jsonPath("$[?(@.messageId == '" + messageId + "')].late", contains(true)))
                .andExpect(jsonPath("$[?(@.messageId == '" + messageId + "')].quality", contains("OK")));
    }

    @Test
    @DisplayName("범위 밖 값(CO2 0ppm)은 저장되지만 OUT_OF_RANGE로 표시된다")
    void outOfRangeTelemetryIsMarked() throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish("1", "vent-s1", telemetryJson(messageId, "vent-s1", now(), 0, ""));

        awaitStored("vent-s1", messageId);
        mockMvc.perform(get("/api/devices/vent-s1/telemetry"))
                .andExpect(jsonPath("$[?(@.messageId == '" + messageId + "')].quality", contains("OUT_OF_RANGE")));
    }

    @Test
    @DisplayName("최신 측정값 조회는 시계 오차 측정값을 건너뛰고 품질이 OK인 것 중 가장 최근 것을 돌려준다")
    void latestSkipsClockSkewedTelemetry() throws Exception {
        String okId = UUID.randomUUID().toString();
        String skewedId = UUID.randomUUID().toString();
        publish("2", "vent-s2", telemetryJson(okId, "vent-s2", now().plusMinutes(1), 820, ""));
        publish("2", "vent-s2", telemetryJson(skewedId, "vent-s2", now().plusMinutes(30), 820, ""));

        awaitStored("vent-s2", skewedId);
        mockMvc.perform(get("/api/devices/vent-s2/telemetry"))
                .andExpect(jsonPath("$[?(@.messageId == '" + skewedId + "')].quality", contains("CLOCK_SKEW")));
        mockMvc.perform(get("/api/telemetry/latest"))
                .andExpect(jsonPath("$[?(@.deviceId == 'vent-s2')].messageId", contains(okId)));
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

    @Test
    @DisplayName("DB가 잠깐 죽은 동안 온 측정값 여러 개는 수신 확인 없이 남았다가, DB가 살아난 뒤 다시 받아 모두 저장된다")
    void telemetryDuringDbOutageIsNotLost() throws Exception {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        AtomicBoolean dbDown = new AtomicBoolean(true);
        AtomicInteger failures = new AtomicInteger();
        doAnswer(invocation -> {
            TelemetryMessage message = invocation.getArgument(2);
            if (dbDown.get() && ids.contains(message.messageId())) {
                failures.incrementAndGet();
                throw new DataAccessResourceFailureException("DB 연결 실패 (테스트에서 끼워 넣음)");
            }
            return invocation.callRealMethod();
        }).when(ingestService).ingest(any(), any(), any(), any());

        // 최신값 테스트(vent-s3)와 겹치지 않도록 목록으로 확인하는 장비(vent-s1)를 쓴다.
        for (UUID id : ids) {
            publish("1", "vent-s1", telemetryJson(id.toString(), "vent-s1", ""));
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> failures.get() >= ids.size());
        dbDown.set(false);

        // 서버가 다시 연결하면 브로커가 확인 응답을 못 받은 메시지를 모두 다시 보낸다.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mockMvc.perform(get("/api/devices/vent-s1/telemetry"))
                        .andExpect(jsonPath("$[*].messageId", hasItem(ids.get(0).toString())))
                        .andExpect(jsonPath("$[*].messageId", hasItem(ids.get(1).toString())))
                        .andExpect(jsonPath("$[*].messageId", hasItem(ids.get(2).toString()))));
    }

    @Test
    @DisplayName("다시 해도 안 되는 DB 오류(무결성 위반)는 다시 받지 않고 버린다. 그래야 같은 메시지가 끝없이 돌아오지 않는다")
    void permanentDbErrorIsNotRetried() throws Exception {
        UUID targetId = UUID.randomUUID();
        String sentinelId = UUID.randomUUID().toString();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            TelemetryMessage message = invocation.getArgument(2);
            if (targetId.equals(message.messageId())) {
                attempts.incrementAndGet();
                throw new DataIntegrityViolationException("무결성 위반 (테스트에서 끼워 넣음)");
            }
            return invocation.callRealMethod();
        }).when(ingestService).ingest(any(), any(), any(), any());

        publish("1", "vent-s1", telemetryJson(targetId.toString(), "vent-s1", ""));
        publish("1", "vent-s1", telemetryJson(sentinelId, "vent-s1", ""));
        awaitStored("vent-s1", sentinelId);

        // 다시 받는다면 재연결 대기(5초) 뒤에 다시 시도된다. 그보다 길게 지켜봐도 한 번뿐이어야 한다.
        await().during(Duration.ofSeconds(8)).atMost(Duration.ofSeconds(12)).until(() -> attempts.get() == 1);
    }

    @Test
    @DisplayName("센서 고장값(습도 65535%)도 DB 오류 없이 OUT_OF_RANGE로 저장된다")
    void sensorFaultValueIsStoredAsOutOfRange() throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish("1", "vent-s1", """
                {"schemaVersion": 1, "messageId": "%s", "deviceId": "vent-s1", "measuredAt": "%s",
                 "co2Ppm": 820, "tempC": 23.4, "humidityPct": 65535,
                 "supplyCmh": 205, "exhaustCmh": 200, "filterPa": 85, "bypass": false}
                """.formatted(messageId, now()));

        awaitStored("vent-s1", messageId);
        mockMvc.perform(get("/api/devices/vent-s1/telemetry"))
                .andExpect(jsonPath("$[?(@.messageId == '" + messageId + "')].quality", contains("OUT_OF_RANGE")));
    }
}
