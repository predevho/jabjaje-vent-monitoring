package vent.servicerequest.rule;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import vent.servicerequest.domain.AssessmentValidationError;

/**
 * AI 소견 검증 결과 (#10).
 *
 * @param passed 통과했는지
 * @param parsed JSON으로 읽은 소견. 형식 오류면 null
 * @param errors 걸린 항목. 통과면 비어 있다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record AssessmentValidation(boolean passed, JsonNode parsed, List<AssessmentValidationError> errors) {
}
