package vent.servicerequest.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.servicerequest.rule.AssessmentValidation;
import vent.servicerequest.rule.AssessmentValidator;
import vent.servicerequest.rule.SituationDevice;
import vent.servicerequest.rule.SituationFilterPaTrend;
import vent.servicerequest.rule.SituationSiteProfile;
import vent.servicerequest.rule.SituationSummary;

/**
 * 가짜 AI 소견 단위 테스트.
 *
 * <p>테스트 지점: 가짜 AI의 공개 메서드. 기준은 스펙 8.7의 시나리오별 정답 원인이다. 정상 응답은 실제 검증을 통과해야
 * 시연에서 소견 완료가 된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
class FakeAssessmentClientTest {

    private static final List<String> PARTS = List.of("DOMEKT R 300V 교체 필터 세트", "VEMC 신호 케이블");
    private static final SituationSiteProfile SITE = new SituationSiteProfile("경기 안성시", new BigDecimal("120.0"), 4, 150, 180,
            List.of(new SituationDevice("VEMC", "VEMC"), new SituationDevice("VENTILATOR", "DOMEKT R 300V")));

    private final FakeAssessmentClient client = new FakeAssessmentClient(new FakeAssessmentProperties(FakeAssessmentMode.NORMAL));
    private final AssessmentValidator validator = new AssessmentValidator();

    @Test
    @DisplayName("필터 차압이 꾸준히 오르면 필터 막힘과 그 모델의 교체 필터를 내고, 검증을 통과한다")
    void filterClogged() {
        SituationSummary summary = summary(1.0, 0.7, new SituationFilterPaTrend(80, 140, 60), 1.0, 800);

        String raw = client.assess(input(summary)).rawText();
        AssessmentValidation validation = validator.validate(raw, summary.availableMetricKeys(), PARTS);

        assertThat(validation.passed()).isTrue();
        assertThat(validation.parsed().at("/causes/0/title").asText()).contains("필터 막힘");
        assertThat(validation.parsed().at("/parts/0").asText()).isEqualTo("DOMEKT R 300V 교체 필터 세트");
    }

    @Test
    @DisplayName("데이터 충분도가 낮으면 다른 지표보다 먼저 판단을 보류한다")
    void lowCoverageWithholds() {
        SituationSummary summary = summary(0.1, 0.7, new SituationFilterPaTrend(80, 140, 60), 1.0, 800);

        String raw = client.assess(input(summary)).rawText();

        assertThat(validator.validate(raw, summary.availableMetricKeys(), PARTS).parsed().at("/causes/0/evidence/0").asText())
                .isEqualTo("dataCoverage");
    }

    @Test
    @DisplayName("장비는 정상인데 CO2가 높으면 목표 풍량 부족으로 보고 TAB을 권한다")
    void tooManyOccupants() {
        SituationSummary summary = summary(1.0, 0.95, new SituationFilterPaTrend(80, 85, 5), 1.0, 1200);

        String raw = client.assess(input(summary)).rawText();

        assertThat(raw).contains("목표 풍량", "TAB");
    }

    private static AssessmentInput input(SituationSummary summary) {
        return new AssessmentInput(List.of(), List.of(), PARTS, summary, "<customer_message>답답해요</customer_message>", 1);
    }

    private static SituationSummary summary(double coverage, double supplyRatio, SituationFilterPaTrend filter, Double hoodRate, int co2) {
        return new SituationSummary(SITE, co2, co2 + 200, supplyRatio, filter, 0.0, 0.0, hoodRate, 0, coverage, List.of(), List.of());
    }
}
