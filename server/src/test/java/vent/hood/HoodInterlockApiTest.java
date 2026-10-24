package vent.hood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import org.springframework.beans.factory.annotation.Autowired;
import vent.hood.service.HoodInterlockCheckService;
import vent.support.MqttIntegrationTest;

/**
 * 후드 연동 감시 통합 테스트.
 *
 * <p>테스트 지점: 실제 브로커로 VEMC 이벤트와 환기장치 측정값을 발행하고, 확인 검사를 직접 부른 뒤 이상 API로 확인한다
 * (확인 스케줄러는 테스트 기반 설정이 끈다). 측정 시각은 30~40분 전이라 모두 늦은 측정값이다. 끊긴 동안의 후드 연동을
 * 다시 연결된 뒤 확인하는 경우와 같다(ADR-0003). 다른 테스트의 측정값·이벤트와 섞이지 않도록 이 테스트 전용 현장
 * (게이트웨이, 환기장치, VEMC 하나씩)을 등록해 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
class HoodInterlockApiTest extends MqttIntegrationTest {

    private static final long SITE = 90;
    private static final String SITE_ID = "90";
    private static final String VENTILATOR = "vent-t90";
    private static final String VEMC = "vemc-t90";
    private static final String VEMC_NO_RESPONSE_OF = "$[?(@.rule == 'VEMC_NO_RESPONSE' && @.deviceId == '%s')]";

    @Autowired
    private HoodInterlockCheckService checkService;

    @BeforeEach
    void registerTestSite() {
        jdbcTemplate.update("""
                INSERT INTO site (id, name, region, floor_area_m2, occupants, target_airflow_cmh, target_airflow_source)
                VALUES (?, '후드 테스트 현장', '테스트', 100.0, 3, 150, 'MINIMUM_AIRFLOW')
                ON CONFLICT (id) DO NOTHING
                """, SITE);
        jdbcTemplate.update("INSERT INTO gateway (id, site_id, connection_state) VALUES ('gw-t90', ?, 'PENDING') "
                + "ON CONFLICT (id) DO NOTHING", SITE);
        registerTestDevice(SITE, "gw-t90", VENTILATOR, "VENTILATOR", "DOMEKT R 300V");
        registerTestDevice(SITE, "gw-t90", VEMC, "VEMC", "VEMC");
    }

    @Test
    @DisplayName("늦게 들어온 측정값으로 확인한다: 급기가 오르지 않으면 VEMC 무반응이 열리고, 다음 연동이 정상이면 해결된다")
    void noResponseOpensAndNextConfirmedResolves() throws Exception {
        // 40분 전 후드 ON: 직전 200CMH → 60초 뒤 204CMH (2% 상승) → 무반응
        OffsetDateTime firstHoodOn = now().minusMinutes(40);
        publishSupply(firstHoodOn.minusSeconds(20), 200);
        publishHoodOn(UUID.randomUUID().toString(), firstHoodOn);
        publishSupply(firstHoodOn.plusSeconds(70), 204);

        checkService.checkPending(now());
        mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(VEMC_NO_RESPONSE_OF.formatted(VEMC), hasSize(1)));

        // 30분 전 후드 ON(그 전에 후드를 끔): 직전 200CMH → 60초 뒤 280CMH (40% 상승) → 정상 → 해결
        OffsetDateTime secondHoodOn = now().minusMinutes(30);
        publishSupply(secondHoodOn.minusSeconds(20), 200);
        publishEvent(SITE_ID, VEMC, eventJson(UUID.randomUUID().toString(), "HOOD_OFF", secondHoodOn.minusSeconds(10)));
        publishHoodOn(UUID.randomUUID().toString(), secondHoodOn);
        publishSupply(secondHoodOn.plusSeconds(70), 280);

        checkService.checkPending(now());
        mockMvc.perform(get("/api/anomalies?status=OPEN"))
                .andExpect(jsonPath(VEMC_NO_RESPONSE_OF.formatted(VEMC), empty()));
        mockMvc.perform(get("/api/anomalies?status=RESOLVED"))
                .andExpect(jsonPath(VEMC_NO_RESPONSE_OF.formatted(VEMC), hasSize(1)));
    }

    @Test
    @DisplayName("같은 messageId의 이벤트를 두 번 보내면 한 건만 저장된다")
    void duplicateEventIsStoredOnce() throws Exception {
        String messageId = UUID.randomUUID().toString();
        OffsetDateTime hoodOn = now().minusMinutes(50);
        publishEvent(SITE_ID, VEMC, eventJson(messageId, "HOOD_ON", hoodOn));
        publishEvent(SITE_ID, VEMC, eventJson(messageId, "HOOD_ON", hoodOn));
        String sentinelId = UUID.randomUUID().toString();
        publishEvent(SITE_ID, VEMC, eventJson(sentinelId, "HOOD_OFF", hoodOn.plusMinutes(5)));

        awaitEventStored(sentinelId);
        assertThat(countEvents(messageId)).isEqualTo(1);
    }

    @Test
    @DisplayName("환기장치가 보낸 이벤트는 저장하지 않는다: 후드 이벤트는 VEMC만 보낸다")
    void eventFromVentilatorIsRejected() throws Exception {
        String messageId = UUID.randomUUID().toString();
        publishEvent(SITE_ID, VENTILATOR, """
                {"schemaVersion": 1, "messageId": "%s", "deviceId": "%s", "type": "HOOD_ON", "occurredAt": "%s"}
                """.formatted(messageId, VENTILATOR, now()));
        String sentinelId = UUID.randomUUID().toString();
        publishEvent(SITE_ID, VEMC, eventJson(sentinelId, "HOOD_OFF", now().minusMinutes(55)));

        awaitEventStored(sentinelId);
        assertThat(countEvents(messageId)).isZero();
    }

    private void publishHoodOn(String messageId, OffsetDateTime occurredAt) throws Exception {
        publishEvent(SITE_ID, VEMC, eventJson(messageId, "HOOD_ON", occurredAt));
        awaitEventStored(messageId);
    }

    private void publishSupply(OffsetDateTime measuredAt, int supplyCmh) throws Exception {
        String messageId = UUID.randomUUID().toString();
        publish(SITE_ID, VENTILATOR, """
                {"schemaVersion": 1, "messageId": "%s", "deviceId": "%s", "measuredAt": "%s",
                 "co2Ppm": 800, "tempC": 23.0, "humidityPct": 45, "supplyCmh": %d, "exhaustCmh": %d,
                 "filterPa": 80, "bypass": false}
                """.formatted(messageId, VENTILATOR, measuredAt, supplyCmh, supplyCmh));
        awaitStored(VENTILATOR, messageId);
    }

    private static String eventJson(String messageId, String type, OffsetDateTime occurredAt) {
        return """
                {"schemaVersion": 1, "messageId": "%s", "deviceId": "%s", "type": "%s", "occurredAt": "%s"}
                """.formatted(messageId, VEMC, type, occurredAt);
    }

    private void awaitEventStored(String messageId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> countEvents(messageId) > 0);
    }

    private int countEvents(String messageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM device_event WHERE message_id = ?::uuid", Integer.class, messageId);
        return count == null ? 0 : count;
    }
}
