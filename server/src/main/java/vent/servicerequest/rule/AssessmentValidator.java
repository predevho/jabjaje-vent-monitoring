package vent.servicerequest.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vent.servicerequest.domain.AssessmentValidationError;

/**
 * AI 소견 검증 (스펙 8.5, ADR-0005, #10).
 *
 * <p>AI가 돌려준 글을 코드로 확인한다. 통과하지 못한 소견은 사람에게 넘긴다.
 * <ul>
 *   <li>형식: JSON인지, 필수 항목(원인 목록과 각 원인의 제목·가능성·분류·근거, 조치, 부품, 방문 필요 여부, 고객 안내문)이
 *       있고 원인 목록이 비어 있지 않은지. 분류는 {@link AssessmentCauseCategory} 중 하나(#12)</li>
 *   <li>근거: 원인의 근거 지표가 이번 상황 요약에 값이 있는 지표인지 (지어낸 근거, 데이터 없는 근거)</li>
 *   <li>부품: 판매 부품 목록에 있는지 (없는 부품)</li>
 *   <li>고객 안내문: 금액(원, 천 원, 만 원), 무상·무료·공짜·비용 없음 약속, 보증·환불·보상 표현이 없는지. 고객 문의에 섞인 지시(프롬프트 인젝션)로
 *       잘못된 약속이 나가는 것을 막는다</li>
 * </ul>
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Component
public class AssessmentValidator {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> LIKELIHOODS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final List<Pattern> FORBIDDEN_PHRASES = List.of(
            Pattern.compile("\\d[\\d,.]*\\s*[천만억]?\\s*원"),
            Pattern.compile("[천만억]\\s*원"),
            Pattern.compile("₩"),
            Pattern.compile("무상"),
            Pattern.compile("무료"),
            Pattern.compile("공짜"),
            Pattern.compile("비용\\s*(없|안\\s*들|부담\\s*없)"),
            Pattern.compile("보증"),
            Pattern.compile("환불"),
            Pattern.compile("보상"));

    /**
     * @param raw        AI가 돌려준 글
     * @param metricKeys 이번 상황 요약에서 값이 있는 지표 이름 ({@link SituationSummary#availableMetricKeys()})
     * @param parts      판매 부품 목록
     */
    public AssessmentValidation validate(String raw, Set<String> metricKeys, Collection<String> parts) {
        JsonNode root;
        try {
            root = JSON.readTree(raw == null ? "" : raw);
        } catch (JsonProcessingException e) {
            return failed(null, List.of(new AssessmentValidationError("INVALID_JSON", "JSON으로 읽을 수 없음: " + e.getOriginalMessage())));
        }
        if (root == null || !root.isObject()) {
            return failed(null, List.of(new AssessmentValidationError("INVALID_JSON", "JSON 객체가 아님")));
        }

        List<AssessmentValidationError> errors = new ArrayList<>();
        checkCauses(root.path("causes"), metricKeys, errors);
        requireArray(root, "technicianActions", errors);
        requireArray(root, "parts", errors);
        if (!root.path("needsVisit").isBoolean()) {
            errors.add(new AssessmentValidationError("MISSING_FIELD", "needsVisit(방문 필요 여부)가 없음"));
        }
        for (JsonNode part : root.path("parts")) {
            if (!parts.contains(part.asText())) {
                errors.add(new AssessmentValidationError("UNKNOWN_PART", "판매 목록에 없는 부품: " + part.asText()));
            }
        }
        checkCustomerReply(root.path("customerReply"), errors);
        return errors.isEmpty() ? new AssessmentValidation(true, root, List.of()) : failed(root, errors);
    }

    private static void checkCauses(JsonNode causes, Set<String> metricKeys, List<AssessmentValidationError> errors) {
        if (!causes.isArray() || causes.isEmpty()) {
            errors.add(new AssessmentValidationError("MISSING_FIELD", "causes(원인)가 하나도 없음"));
            return;
        }
        for (JsonNode cause : causes) {
            if (cause.path("title").asText("").isBlank()) {
                errors.add(new AssessmentValidationError("MISSING_FIELD", "원인 제목이 없음"));
            }
            if (!LIKELIHOODS.contains(cause.path("likelihood").asText())) {
                errors.add(new AssessmentValidationError("INVALID_FIELD", "가능성은 HIGH/MEDIUM/LOW 중 하나: " + cause.path("likelihood")));
            }
            if (!AssessmentCauseCategory.isKnown(cause.path("category").asText())) {
                errors.add(new AssessmentValidationError("INVALID_FIELD", "원인 분류가 정해진 값이 아님: " + cause.path("category")));
            }
            JsonNode evidence = cause.path("evidence");
            if (!evidence.isArray() || evidence.isEmpty()) {
                errors.add(new AssessmentValidationError("MISSING_FIELD", "원인의 근거가 없음: " + cause.path("title").asText()));
                continue;
            }
            for (JsonNode key : evidence) {
                if (!metricKeys.contains(key.asText())) {
                    errors.add(new AssessmentValidationError("UNKNOWN_EVIDENCE", "상황 요약에 없거나 값이 없는 근거: " + key.asText()));
                }
            }
        }
    }

    private static void requireArray(JsonNode root, String field, List<AssessmentValidationError> errors) {
        if (!root.path(field).isArray()) {
            errors.add(new AssessmentValidationError("MISSING_FIELD", field + "가 없음"));
        }
    }

    private static void checkCustomerReply(JsonNode reply, List<AssessmentValidationError> errors) {
        String text = reply.asText("");
        if (!reply.isTextual() || text.isBlank()) {
            errors.add(new AssessmentValidationError("MISSING_FIELD", "customerReply(고객 안내문)가 없음"));
            return;
        }
        for (Pattern pattern : FORBIDDEN_PHRASES) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                errors.add(new AssessmentValidationError("FORBIDDEN_PHRASE", "고객 안내문에 쓸 수 없는 표현: " + matcher.group()));
            }
        }
    }

    private static AssessmentValidation failed(JsonNode parsed, List<AssessmentValidationError> errors) {
        return new AssessmentValidation(false, parsed, List.copyOf(errors));
    }
}
