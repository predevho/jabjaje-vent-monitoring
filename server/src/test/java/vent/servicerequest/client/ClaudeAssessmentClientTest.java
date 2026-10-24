package vent.servicerequest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.servicerequest.rule.SituationAnomaly;
import vent.servicerequest.rule.SituationDevice;
import vent.servicerequest.rule.SituationFilterPaTrend;
import vent.servicerequest.rule.SituationSiteProfile;
import vent.servicerequest.rule.SituationSummary;

/**
 * 실제 Claude AI 소견 호출부 테스트.
 *
 * <p>테스트 지점: Claude 호출부의 공개 메서드와 그것이 보내는 HTTP 요청. Anthropic API 대신 가짜 HTTP 서버를 띄워 실제 비용
 * 없이 확인한다. 기준은 #12 결정이다: 시스템 프롬프트(캐싱)와 사용자 메시지(상황 요약, 구분 태그 안 고객 문의), 구조화
 * 출력 스키마, effort, 서버 재실행(fallbacks), 재시도 모델, 그리고 시간 초과·거절·응답 잘림을 실패 코드로 바꾸는 것.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
class ClaudeAssessmentClientTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<String> PARTS = List.of("DOMEKT R 300V 교체 필터 세트", "VEMC 신호 케이블");
    private static final String ASSESSMENT = """
            {"causes": [{"title": "필터 막힘", "likelihood": "HIGH", "category": "FILTER_CLOGGED", "evidence": ["filterPaTrend"]}],
             "technicianActions": ["필터 교체"], "parts": ["DOMEKT R 300V 교체 필터 세트"], "needsVisit": false,
             "recommendedServices": [], "customerReply": "필터가 막혀 있어요."}
            """;

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("응답 글과 실제로 답한 모델, 토큰 사용량을 돌려준다")
    void returnsTextModelAndUsage() {
        server.enqueue(message("end_turn", "claude-opus-5-5", ASSESSMENT));

        AssessmentReply reply = client(Duration.ofSeconds(5)).assess(input(1));

        assertThat(reply.rawText()).contains("FILTER_CLOGGED");
        assertThat(reply.model()).isEqualTo("claude-opus-5-5");
        assertThat(reply.usage()).isEqualTo(new AssessmentUsage(120, 340, 0, 1500));
    }

    @Test
    @DisplayName("시스템 프롬프트(캐싱)·사용자 메시지·구조화 출력 스키마·effort·서버 재실행을 담아 보낸다")
    void sendsPromptSchemaAndOptions() throws Exception {
        server.enqueue(message("end_turn", "claude-opus-5-5", ASSESSMENT));

        client(Duration.ofSeconds(5)).assess(input(1));

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        JsonNode body = JSON.readTree(request.getBody().readUtf8());
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("test-key");
        assertThat(request.getHeader("anthropic-beta")).contains(ClaudeAssessmentClient.FALLBACK_BETA);
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(body.at("/system/0/cache_control/type").asText()).isEqualTo("ephemeral");
        assertThat(body.at("/system/0/text").asText()).contains("소견 기준표", "VEMC 신호 케이블", "customer_message");
        String user = body.at("/messages/0/content").toString();
        assertThat(user).contains("filterPaTrend", "<customer_message>", "[연락처]");
        assertThat(body.at("/output_config/effort").asText()).isEqualTo("medium");
        assertThat(body.at("/output_config/format/schema/properties/causes/items/properties/category/enum").toString())
                .contains("FILTER_CLOGGED", "INSUFFICIENT_DATA");
        assertThat(body.at("/output_config/format/schema/properties/parts/items/enum").toString()).contains("VEMC 신호 케이블");
    }

    @Test
    @DisplayName("재시도(2번째 시도)는 재시도 모델로 부른다 (단계적 승격)")
    void retryUsesRetryModel() throws Exception {
        server.enqueue(message("end_turn", "claude-opus-5-5", ASSESSMENT));

        client(Duration.ofSeconds(5), "claude-haiku-5-5", "claude-opus-5-5").assess(input(2));

        JsonNode body = JSON.readTree(server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
    }

    @Test
    @DisplayName("서버 재실행이 없는 모델(Haiku 5.5)에는 fallbacks를 보내지 않는다")
    void noServerFallbackForHaiku() throws Exception {
        server.enqueue(message("end_turn", "claude-haiku-5-5", ASSESSMENT));

        client(Duration.ofSeconds(5), "claude-haiku-5-5", "claude-opus-5-5").assess(input(1));

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(JSON.readTree(request.getBody().readUtf8()).has("fallbacks")).isFalse();
        assertThat(request.getHeader("anthropic-beta")).isNull();
    }

    @Test
    @DisplayName("프롬프트의 시각은 ISO 문자열이다 (운영과 평가 세트가 같은 프롬프트를 보낸다)")
    void timestampsAreIsoStrings() throws Exception {
        server.enqueue(message("end_turn", "claude-opus-5-5", ASSESSMENT));
        SituationSummary summary = new SituationSummary(
                new SituationSiteProfile("경기 안성시", new BigDecimal("120.0"), 4, 150, 180, List.of()),
                800, 1000, 0.97, null, 0.0, 0.0, null, 0, 0.98,
                List.of(new SituationAnomaly("FILTER_CLOGGED", "WARN", "OPEN",
                        OffsetDateTime.parse("2026-10-22T03:10:00Z"))), List.of());

        client(Duration.ofSeconds(5)).assess(
                new AssessmentInput(AssessmentPrompt.VERSION, PARTS, summary, "<customer_message>x</customer_message>", 1));

        String user = JSON.readTree(server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8())
                .at("/messages/0/content").toString();
        assertThat(user).contains("2026-10-22T03:10:00Z");
    }

    @Test
    @DisplayName("안전 분류기가 거절하면 REFUSAL (다시 시도하지 않음)")
    void refusal() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5", "content": [],
                 "stop_reason": "refusal", "stop_sequence": null,
                 "stop_details": {"type": "refusal", "category": "cyber", "explanation": "x"},
                 "usage": {"input_tokens": 10, "output_tokens": 0}}
                """));

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).assess(input(1)))
                .isInstanceOfSatisfying(AssessmentClientException.class, e -> {
                    assertThat(e.code()).isEqualTo(AssessmentClientException.REFUSAL);
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.model()).isEqualTo("claude-opus-5-5");
                });
    }

    @Test
    @DisplayName("응답 길이 한도에 걸려 잘리면 TRUNCATED")
    void truncated() {
        server.enqueue(message("max_tokens", "claude-opus-5-5", "{\"causes\": ["));

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).assess(input(1)))
                .isInstanceOfSatisfying(AssessmentClientException.class,
                        e -> assertThat(e.code()).isEqualTo(AssessmentClientException.TRUNCATED));
    }

    @Test
    @DisplayName("시간 안에 응답이 없거나 서버 오류면 CLIENT_ERROR")
    void timeoutAndServerError() {
        server.enqueue(message("end_turn", "claude-opus-5-5", ASSESSMENT).setHeadersDelay(3, TimeUnit.SECONDS));
        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).assess(input(1)))
                .isInstanceOfSatisfying(AssessmentClientException.class,
                        e -> assertThat(e.code()).isEqualTo(AssessmentClientException.CLIENT_ERROR));

        server.enqueue(new MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json")
                .setBody("{\"type\": \"error\", \"error\": {\"type\": \"api_error\", \"message\": \"down\"}}"));
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).assess(input(1)))
                .isInstanceOfSatisfying(AssessmentClientException.class, e -> {
                    assertThat(e.code()).isEqualTo(AssessmentClientException.CLIENT_ERROR);
                    assertThat(e.retryable()).isTrue();
                });
    }

    @Test
    @DisplayName("인증 실패(401)는 CLIENT_ERROR지만 다시 해도 같으니 다시 시도하지 않는다")
    void unauthorizedIsNotRetryable() {
        server.enqueue(new MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("{\"type\": \"error\", \"error\": {\"type\": \"authentication_error\", \"message\": \"bad key\"}}"));

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).assess(input(1)))
                .isInstanceOfSatisfying(AssessmentClientException.class, e -> {
                    assertThat(e.code()).isEqualTo(AssessmentClientException.CLIENT_ERROR);
                    assertThat(e.retryable()).isFalse();
                });
    }

    private ClaudeAssessmentClient client(Duration timeout) {
        return client(timeout, "claude-opus-5-5", "claude-opus-5-5");
    }

    private ClaudeAssessmentClient client(Duration timeout, String model, String retryModel) {
        ClaudeAssessmentProperties properties = new ClaudeAssessmentProperties(model, retryModel, "medium", 16000, timeout, 0,
                true, server.url("/").toString());
        var anthropic = AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl(properties.baseUrl())
                .timeout(timeout)
                .maxRetries(0)
                .build();
        return new ClaudeAssessmentClient(anthropic, properties, new AssessmentPrompt());
    }

    private static MockResponse message(String stopReason, String model, String text) {
        try {
            String content = JSON.writeValueAsString(List.of(java.util.Map.of("type", "text", "text", text)));
            return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id": "msg_1", "type": "message", "role": "assistant", "model": "%s", "content": %s,
                     "stop_reason": "%s", "stop_sequence": null,
                     "usage": {"input_tokens": 120, "output_tokens": 340, "cache_read_input_tokens": 0,
                               "cache_creation_input_tokens": 1500}}
                    """.formatted(model, content, stopReason));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static AssessmentInput input(int attempt) {
        SituationSummary summary = new SituationSummary(
                new SituationSiteProfile("경기 안성시", new BigDecimal("120.0"), 4, 150, 180,
                        List.of(new SituationDevice("VENTILATOR", "DOMEKT R 300V"))),
                800, 1000, 0.72, new SituationFilterPaTrend(80, 150, 70), 0.0, 0.0, null, 0, 0.98, List.of(), List.of());
        return new AssessmentInput(AssessmentPrompt.VERSION, PARTS, summary,
                "<customer_message>\n바람이 약해요 [연락처]\n</customer_message>", attempt);
    }
}
