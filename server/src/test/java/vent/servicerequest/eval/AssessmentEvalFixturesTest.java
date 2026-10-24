package vent.servicerequest.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vent.servicerequest.client.AssessmentInput;
import vent.servicerequest.client.AssessmentPrompt;
import vent.servicerequest.client.FakeAssessmentClient;
import vent.servicerequest.client.FakeAssessmentMode;
import vent.servicerequest.client.FakeAssessmentProperties;
import vent.servicerequest.rule.AssessmentCauseCategory;
import vent.servicerequest.rule.AssessmentValidation;
import vent.servicerequest.rule.AssessmentValidator;
import vent.servicerequest.rule.SituationSummary;

/**
 * 평가 시나리오 파일 점검 테스트.
 *
 * <p>테스트 지점: 평가 세트가 읽는 시나리오 파일과 채점 기준. 실제 AI는 부르지 않는다(비용 없음). 시나리오 파일이 상황 요약으로
 * 읽히는지, 정답 분류가 정해진 값인지, 가짜 AI가 정답을 내고 검증을 통과하는지 본다. 가짜 AI가 틀리면 시나리오 데이터가
 * 정답과 어긋난 것이다(스펙 8.7).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
class AssessmentEvalFixturesTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<String> PARTS = List.of("DOMEKT R 300V 교체 필터 세트", "DOMEKT R 450V 교체 필터 세트",
            "VEMC 신호 케이블", "급기 디퓨저", "배기 그릴");

    @ParameterizedTest
    @ValueSource(strings = {"1-filter-clogged", "2-too-many-occupants", "3-vemc-not-linked", "4-normal-but-stuffy",
            "5-gateway-offline"})
    void fixtureIsValidAndFakeAnswersIt(String id) throws Exception {
        JsonNode fixture;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("eval/" + id + ".json")) {
            fixture = JSON.readTree(in);
        }
        SituationSummary summary = JSON.treeToValue(fixture.path("summary"), SituationSummary.class);
        List<String> expected = new ArrayList<>();
        fixture.path("expected").forEach(node -> expected.add(node.asText()));
        assertThat(expected).isNotEmpty().allMatch(AssessmentCauseCategory::isKnown);

        String raw = new FakeAssessmentClient(new FakeAssessmentProperties(FakeAssessmentMode.NORMAL))
                .assess(new AssessmentInput(AssessmentPrompt.VERSION, PARTS, summary, "<customer_message>x</customer_message>", 1))
                .rawText();
        AssessmentValidation validation = new AssessmentValidator().validate(raw, summary.availableMetricKeys(), PARTS);

        assertThat(validation.passed()).isTrue();
        assertThat(expected).contains(validation.parsed().at("/causes/0/category").asText());
    }
}
