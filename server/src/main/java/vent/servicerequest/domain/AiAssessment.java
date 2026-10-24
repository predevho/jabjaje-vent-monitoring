package vent.servicerequest.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 소견 엔티티 ({@code ai_assessment} 테이블).
 *
 * <p>AI가 A/S 신청에 대해 낸 원인과 조치의 초안(용어집). 고객에게 그대로 나가지 않는다(ADR-0005). 시도마다 한 행이고,
 * AI에 보낸 입력 사본과 검증 결과를 함께 남긴다(#10).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Entity
public class AiAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long serviceRequestId;

    private int attempt;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode result;

    private String rawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<AssessmentValidationError> validationErrors;

    private boolean passed;

    private String model;

    private OffsetDateTime createdAt;

    protected AiAssessment() {
    }

    /**
     * 시도 한 번의 기록.
     *
     * @param result      JSON으로 읽힌 소견. 형식 오류면 null
     * @param rawResponse AI가 돌려준 원문. AI를 부르지 못했으면 null
     */
    public static AiAssessment attempt(Long serviceRequestId, int attempt, JsonNode inputSnapshot, JsonNode result,
                                       String rawResponse, List<AssessmentValidationError> validationErrors, String model,
                                       OffsetDateTime now) {
        AiAssessment assessment = new AiAssessment();
        assessment.serviceRequestId = serviceRequestId;
        assessment.attempt = attempt;
        assessment.inputSnapshot = inputSnapshot;
        assessment.result = result;
        assessment.rawResponse = rawResponse;
        assessment.validationErrors = List.copyOf(validationErrors);
        assessment.passed = validationErrors.isEmpty();
        assessment.model = model;
        assessment.createdAt = now;
        return assessment;
    }

    public Long getId() {
        return id;
    }

    public Long getServiceRequestId() {
        return serviceRequestId;
    }

    public int getAttempt() {
        return attempt;
    }

    public JsonNode getInputSnapshot() {
        return inputSnapshot;
    }

    public JsonNode getResult() {
        return result;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public List<AssessmentValidationError> getValidationErrors() {
        return validationErrors;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getModel() {
        return model;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
