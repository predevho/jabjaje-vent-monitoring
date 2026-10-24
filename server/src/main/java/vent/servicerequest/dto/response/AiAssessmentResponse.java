package vent.servicerequest.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import vent.servicerequest.domain.AiAssessment;
import vent.servicerequest.domain.AssessmentValidationError;

/**
 * AI 소견 시도 한 번 응답 (#10).
 *
 * @param attempt          몇 번째 시도인지
 * @param passed           검증 통과 여부
 * @param model            응답한 모델
 * @param result           JSON으로 읽힌 소견. 형식 오류면 null
 * @param rawResponse      응답 원문. 형식 오류일 때 직원이 원문을 보게 남긴다
 * @param validationErrors 검증에 걸린 이유
 * @param inputSnapshot    AI에 보낸 입력 사본 (고객 이름·연락처 없음)
 * @param createdAt        시도 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record AiAssessmentResponse(int attempt, boolean passed, String model, JsonNode result, String rawResponse,
                                   List<AssessmentValidationError> validationErrors, JsonNode inputSnapshot,
                                   OffsetDateTime createdAt) {

    public static AiAssessmentResponse from(AiAssessment a) {
        return new AiAssessmentResponse(a.getAttempt(), a.isPassed(), a.getModel(), a.getResult(),
                a.getResult() == null ? a.getRawResponse() : null, a.getValidationErrors(), a.getInputSnapshot(),
                a.getCreatedAt());
    }
}
