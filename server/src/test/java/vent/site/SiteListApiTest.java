package vent.site;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 현장 목록 API 통합 테스트.
 *
 * <p>테스트 지점: 실제 PostgreSQL에 마이그레이션과 시드를 적용한 상태의 현장 목록 API.
 * 기대값은 코드가 아니라 스펙의 시드 계획(현장 3곳)과 5.3 계산식에서 가져왔다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("demo")
// 이 테스트는 MQTT가 필요 없다. 켜 두면 로컬에서 실행 중인 브로커에 같은 클라이언트 ID로 붙어 시연 서버를 끊을 수 있다.
@TestPropertySource(properties = "vent.mqtt.enabled=false")
class SiteListApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("현장 목록은 시드된 현장 3곳과 각 현장의 게이트웨이, 장비를 돌려준다")
    void listsSeededSitesWithGatewayAndDevices() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))

                // 양평: TAB으로 맞춘 목표 풍량이 있는 현장
                .andExpect(jsonPath("$[0].name").value("양평 현장"))
                .andExpect(jsonPath("$[0].region").value("경기 양평군"))
                .andExpect(jsonPath("$[0].floorAreaM2").value(132.0))
                .andExpect(jsonPath("$[0].occupants").value(4))
                .andExpect(jsonPath("$[0].targetAirflowCmh").value(210))
                .andExpect(jsonPath("$[0].targetAirflowSource").value("TAB"))
                // 최소 환기량: 4명 × 30 = 120 < 132m² × 1.5 = 198. TAB 값(210)이 있어 목표 풍량과 다르다
                .andExpect(jsonPath("$[0].minimumAirflowCmh").value(198))

                // 안성: TAB 값이 없어 목표 풍량 = 최소 환기량(99m² × 2.5m × 0.6회/h = 148.5 → 올림 149)
                .andExpect(jsonPath("$[1].name").value("안성 현장"))
                .andExpect(jsonPath("$[1].targetAirflowCmh").value(149))
                .andExpect(jsonPath("$[1].targetAirflowSource").value("MINIMUM_AIRFLOW"))
                .andExpect(jsonPath("$[1].minimumAirflowCmh").value(149))

                // 서대문: 바닥면적이 커서 450V 모델(165m² × 2.5m × 0.6회/h = 247.5 → 올림 248)
                .andExpect(jsonPath("$[2].name").value("서대문 현장"))
                .andExpect(jsonPath("$[2].targetAirflowCmh").value(248))
                .andExpect(jsonPath("$[2].targetAirflowSource").value("MINIMUM_AIRFLOW"))
                .andExpect(jsonPath("$[2].minimumAirflowCmh").value(248));

        // 현장마다 게이트웨이 1개(연결 전), VEMC 1대, 열회수 환기장치 1대
        String[] ventilatorModels = {"DOMEKT R 300V", "DOMEKT R 300V", "DOMEKT R 450V"};
        for (int i = 0; i < 3; i++) {
            int siteNo = i + 1;
            result
                    .andExpect(jsonPath("$[%d].gateway.id", i).value("gw-s" + siteNo))
                    .andExpect(jsonPath("$[%d].gateway.connectionState", i).value("PENDING"))
                    .andExpect(jsonPath("$[%d].devices.length()", i).value(2))
                    .andExpect(jsonPath("$[%d].devices[0].id", i).value("vemc-s" + siteNo))
                    .andExpect(jsonPath("$[%d].devices[0].type", i).value("VEMC"))
                    .andExpect(jsonPath("$[%d].devices[1].id", i).value("vent-s" + siteNo))
                    .andExpect(jsonPath("$[%d].devices[1].type", i).value("VENTILATOR"))
                    .andExpect(jsonPath("$[%d].devices[1].model", i).value(ventilatorModels[i]));
        }
    }
}
