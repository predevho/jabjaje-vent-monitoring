package vent.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import vent.support.MqttIntegrationTest;

/**
 * 판정 통합 테스트.
 *
 * <p>테스트 지점: 판정 API와 고객 안내 API. AI 소견 과정(#10 테스트가 확인)과 섞이지 않게 신청과 AI 소견을 SQL로 직접
 * 넣고(전용 현장 96번), 그대로 판정·고쳐서 판정·처음부터 판정과 판정 전후의 고객 안내를 확인한다(스펙 8.1, ADR-0005).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
class VerdictApiTest extends MqttIntegrationTest {

    private static final long SITE = 96;
    private static final String AI_RESULT = """
            {"causes": [{"title": "필터 막힘으로 급기 부족", "likelihood": "HIGH", "evidence": ["filterPaTrend"]}],
             "technicianActions": ["필터 교체", "교체 후 풍량 재측정"],
             "parts": ["DOMEKT R 300V 교체 필터 세트"],
             "needsVisit": false,
             "recommendedServices": [],
             "customerReply": "최근 필터가 많이 막혀 공기가 충분히 들어오지 않고 있어요."}
            """;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpSite() {
        registerTestSite(SITE, "gw-t96");
    }

    @Test
    @DisplayName("AI 소견을 그대로 판정하면 판정됨이 되고, 고치지 않음과 참고한 AI 소견이 기록된다")
    void decideAsIs() throws Exception {
        long id = insertAssessedRequest();
        long assessmentId = passedAssessmentId(id);

        decide(id, aiContent("김담당"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiAssessmentId").value(assessmentId))
                .andExpect(jsonPath("$.editedFromAi").value(false))
                .andExpect(jsonPath("$.editedFields").isEmpty())
                .andExpect(jsonPath("$.decidedBy").value("김담당"));

        mockMvc.perform(get("/api/service-requests/" + id))
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.verdict.causes[0]").value("필터 막힘으로 급기 부족"));
    }

    @Test
    @DisplayName("고쳐서 판정하면 고침과 고친 항목이 기록된다 (서버가 AI 소견과 비교)")
    void decideWithEdits() throws Exception {
        long id = insertAssessedRequest();
        Map<String, Object> body = aiContent("김담당");
        body.put("needsVisit", true);
        body.put("customerReply", "담당자가 방문해 필터를 교체해 드릴게요.");

        decide(id, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editedFromAi").value(true))
                .andExpect(jsonPath("$.editedFields[0]").value("NEEDS_VISIT"))
                .andExpect(jsonPath("$.editedFields[1]").value("CUSTOMER_REPLY"));
    }

    @Test
    @DisplayName("사람 확인 필요 건은 AI 소견 없이 처음부터 판정한다")
    void decideFromScratch() throws Exception {
        long id = insertRequest("NEEDS_HUMAN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("causes", List.of("VEMC 신호 배선 불량"));
        body.put("technicianActions", List.of("배선 점검"));
        body.put("parts", List.of("VEMC 신호 케이블"));
        body.put("needsVisit", true);
        body.put("recommendedServices", List.of());
        body.put("customerReply", "후드와 환기장치 연결을 점검하러 방문할게요.");
        body.put("decidedBy", "이담당");

        decide(id, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiAssessmentId").value(nullValue()))
                .andExpect(jsonPath("$.editedFromAi").value(false));
    }

    @Test
    @DisplayName("판정 전에는 고객 안내문이 없고, 판정 뒤에만 확정된 안내가 나온다 (AI 초안은 나오지 않는다)")
    void customerReplyOnlyAfterVerdict() throws Exception {
        long id = insertAssessedRequest();

        mockMvc.perform(get("/api/service-requests/" + id + "/customer-reply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSESSED"))
                .andExpect(jsonPath("$.reply").value(nullValue()));

        Map<String, Object> body = aiContent("김담당");
        body.put("customerReply", "필터 교체 안내를 문자로 보내 드릴게요.");
        decide(id, body).andExpect(status().isCreated());

        mockMvc.perform(get("/api/service-requests/" + id + "/customer-reply"))
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.reply").value("필터 교체 안내를 문자로 보내 드릴게요."));
    }

    @Test
    @DisplayName("AI 소견을 만드는 중이거나 이미 판정된 신청은 409")
    void conflicts() throws Exception {
        long received = insertRequest("RECEIVED");
        decide(received, aiContent("김담당"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("AI 소견을 만드는 중")));

        long assessed = insertAssessedRequest();
        decide(assessed, aiContent("김담당")).andExpect(status().isCreated());
        decide(assessed, aiContent("이담당"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("이미 판정된")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM verdict WHERE service_request_id = ?", Integer.class, assessed))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("원인·안내문·담당자가 비었거나 판매 목록에 없는 부품이면 400이고 판정되지 않는다")
    void invalidVerdicts() throws Exception {
        long id = insertAssessedRequest();

        Map<String, Object> noCause = aiContent("김담당");
        noCause.put("causes", List.of(" "));
        decide(id, noCause).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("원인")));

        Map<String, Object> noReply = aiContent("김담당");
        noReply.put("customerReply", "");
        decide(id, noReply).andExpect(status().isBadRequest());

        decide(id, aiContent(" ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("담당자")));

        Map<String, Object> unknownPart = aiContent("김담당");
        unknownPart.put("parts", List.of("만능 필터 X"));
        decide(id, unknownPart).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("만능 필터 X")));

        mockMvc.perform(get("/api/service-requests/" + id)).andExpect(jsonPath("$.status").value("ASSESSED"));
    }

    @Test
    @DisplayName("판정에 고를 수 있는 판매 부품 목록을 준다 (신청 ID 경로와 섞이지 않는다)")
    void partCatalog() throws Exception {
        mockMvc.perform(get("/api/service-requests/parts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("DOMEKT R 300V 교체 필터 세트"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    private ResultActions decide(long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/service-requests/" + id + "/verdict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 화면이 AI 소견으로 미리 채운 판정 내용 ("그대로 판정"이 보내는 것). */
    private static Map<String, Object> aiContent(String decidedBy) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("causes", List.of("필터 막힘으로 급기 부족"));
        body.put("technicianActions", List.of("필터 교체", "교체 후 풍량 재측정"));
        body.put("parts", List.of("DOMEKT R 300V 교체 필터 세트"));
        body.put("needsVisit", false);
        body.put("recommendedServices", List.of());
        body.put("customerReply", "최근 필터가 많이 막혀 공기가 충분히 들어오지 않고 있어요.");
        body.put("decidedBy", decidedBy);
        return body;
    }

    private long insertRequest(String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service_request (site_id, customer_message, status, created_at)
                VALUES (?, '환기가 약해요', ?, now()) RETURNING id
                """, Long.class, SITE, status);
    }

    /** 소견 완료 신청: 첫 시도는 검증 실패, 두 번째 시도가 통과. */
    private long insertAssessedRequest() {
        long id = insertRequest("ASSESSED");
        jdbcTemplate.update("""
                INSERT INTO ai_assessment (service_request_id, attempt, input_snapshot, result, raw_response, validation_errors,
                                           passed, model, created_at)
                VALUES (?, 1, '{}', '{"causes": []}', '{"causes": []}', '[{"code": "MISSING_FIELD", "message": "원인 없음"}]',
                        false, 'fake-assessment-1', now()),
                       (?, 2, '{}', ?::jsonb, ?, '[]', true, 'fake-assessment-1', now())
                """, id, id, AI_RESULT, AI_RESULT);
        return id;
    }

    private long passedAssessmentId(long serviceRequestId) {
        return jdbcTemplate.queryForObject("SELECT id FROM ai_assessment WHERE service_request_id = ? AND passed",
                Long.class, serviceRequestId);
    }
}
