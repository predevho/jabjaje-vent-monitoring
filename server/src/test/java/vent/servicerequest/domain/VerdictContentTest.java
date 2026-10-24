package vent.servicerequest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 판정 내용 단위 테스트.
 *
 * <p>테스트 지점: AI 소견 JSON에서 판정 항목을 꺼내는 메서드와 생성자. 기준은 스펙 8.4의 AI 소견 형식이다. 판정 양식은 이
 * 내용으로 미리 채우고, 서버는 이 내용과 비교해 고친 항목을 정한다(#11).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
class VerdictContentTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("AI 소견에서 원인은 제목만 순서대로 꺼내고, 나머지 항목은 그대로 꺼낸다")
    void fromAssessment() throws Exception {
        VerdictContent content = VerdictContent.fromAssessment(JSON.readTree("""
                {"causes": [{"title": "필터 막힘", "likelihood": "HIGH", "evidence": ["filterPaTrend"]},
                            {"title": "덕트 막힘", "likelihood": "LOW", "evidence": ["supplyToTargetRatio"]}],
                 "technicianActions": ["필터 교체"], "parts": ["VEMC 신호 케이블"], "needsVisit": true,
                 "recommendedServices": ["TAB"], "customerReply": "방문할게요."}
                """));

        assertThat(content).isEqualTo(new VerdictContent(List.of("필터 막힘", "덕트 막힘"), List.of("필터 교체"),
                List.of("VEMC 신호 케이블"), true, List.of("TAB"), "방문할게요."));
    }

    @Test
    @DisplayName("빠진 항목은 빈 목록, 방문 필요 없음, 빈 안내문이 된다")
    void missingFieldsBecomeEmpty() throws Exception {
        VerdictContent content = VerdictContent.fromAssessment(JSON.readTree("{\"causes\": []}"));

        assertThat(content.technicianActions()).isEmpty();
        assertThat(content.needsVisit()).isFalse();
        assertThat(content.customerReply()).isEmpty();
    }

    @Test
    @DisplayName("목록이 null이면 빈 목록으로, 목록 안의 null은 빈 글자로 바꾼다")
    void nullsAreNormalized() {
        VerdictContent content = new VerdictContent(null, Arrays.asList("교체", null), null, false, null, "안내");

        assertThat(content.causes()).isEmpty();
        assertThat(content.technicianActions()).containsExactly("교체", "");
        assertThat(content.recommendedServices()).isEmpty();
    }
}
