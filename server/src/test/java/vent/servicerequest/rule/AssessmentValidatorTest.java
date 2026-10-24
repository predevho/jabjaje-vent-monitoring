package vent.servicerequest.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.servicerequest.domain.AssessmentValidationError;

/**
 * AI 소견 검증 단위 테스트.
 *
 * <p>테스트 지점: 검증 클래스의 공개 메서드. 기준은 스펙 8.5(형식과 필수 항목, 근거 지표가 상황 요약에 있는지, 부품이 판매
 * 목록에 있는지, 고객 안내문에 금액·무상 교체 약속·보증 표현이 없는지)이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
class AssessmentValidatorTest {

    private static final Set<String> METRICS = Set.of("filterPaTrend", "supplyToTargetRatio", "co2AvgPpm", "dataCoverage");
    private static final List<String> PARTS = List.of("DOMEKT R 300V 교체 필터 세트", "VEMC 신호 케이블");
    private static final String VALID = """
            {"causes": [{"title": "필터 막힘으로 급기 부족", "likelihood": "HIGH",
                         "evidence": ["filterPaTrend", "supplyToTargetRatio"]}],
             "technicianActions": ["필터 교체", "교체 후 풍량 재측정"],
             "parts": ["DOMEKT R 300V 교체 필터 세트"],
             "needsVisit": false,
             "recommendedServices": [],
             "customerReply": "최근 필터가 많이 막혀 공기가 충분히 들어오지 않고 있어요. 담당자가 곧 연락드릴게요."}
            """;

    private final AssessmentValidator validator = new AssessmentValidator();

    @Test
    @DisplayName("형식, 근거, 부품, 안내문이 모두 맞으면 통과")
    void validAssessmentPasses() {
        AssessmentValidation validation = validate(VALID);

        assertThat(validation.passed()).isTrue();
        assertThat(validation.errors()).isEmpty();
    }

    @Test
    @DisplayName("판매 목록에 없는 부품은 걸린다")
    void unknownPartFails() {
        AssessmentValidation validation = validate(VALID.replace("DOMEKT R 300V 교체 필터 세트\"]", "만능 필터 X\"]"));

        assertThat(codes(validation)).containsExactly("UNKNOWN_PART");
    }

    @Test
    @DisplayName("상황 요약에 없거나 값이 없는 근거 지표는 걸린다 (지어낸 근거)")
    void unknownEvidenceFails() {
        assertThat(codes(validate(VALID.replace("\"supplyToTargetRatio\"", "\"humidityTrend\"")))).containsExactly("UNKNOWN_EVIDENCE");
        // hoodInterlockSuccessRate는 상황 요약의 지표지만 이번에는 값이 없다
        assertThat(codes(validate(VALID.replace("\"supplyToTargetRatio\"", "\"hoodInterlockSuccessRate\"")))).containsExactly("UNKNOWN_EVIDENCE");
    }

    @Test
    @DisplayName("고객 안내문에 금액, 무상 교체 약속, 보증 표현이 있으면 걸린다")
    void forbiddenPhrasesFail() {
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "무상으로 교체해 드릴게요")))).containsExactly("FORBIDDEN_PHRASE");
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "출장비 30,000원이 들어요")))).containsExactly("FORBIDDEN_PHRASE");
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "보증 기간이라 괜찮아요")))).containsExactly("FORBIDDEN_PHRASE");
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "필터값 5천원만 받을게요")))).containsExactly("FORBIDDEN_PHRASE");
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "3만 원이면 돼요")))).containsExactly("FORBIDDEN_PHRASE");
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "공짜로 해 드릴게요")))).containsExactly("FORBIDDEN_PHRASE");
        assertThat(codes(validate(VALID.replace("곧 연락드릴게요", "비용 없이 바꿔 드려요")))).containsExactly("FORBIDDEN_PHRASE");
    }

    @Test
    @DisplayName("JSON이 아니거나 필수 항목이 빠지면 형식 오류")
    void brokenFormatFails() {
        assertThat(codes(validate("{\"causes\": ["))).containsExactly("INVALID_JSON");
        assertThat(codes(validate(VALID.replace("\"customerReply\"", "\"reply\"")))).containsExactly("MISSING_FIELD");
        assertThat(codes(validate(VALID.replace("\"HIGH\"", "\"VERY_HIGH\"")))).containsExactly("INVALID_FIELD");
        assertThat(codes(validate(VALID.replaceFirst("(?s)\\[\\{\"title\".*?\\}\\]", "[]")))).containsExactly("MISSING_FIELD");
    }

    private AssessmentValidation validate(String raw) {
        return validator.validate(raw, METRICS, PARTS);
    }

    private static List<String> codes(AssessmentValidation validation) {
        return validation.errors().stream().map(AssessmentValidationError::code).distinct().toList();
    }
}
