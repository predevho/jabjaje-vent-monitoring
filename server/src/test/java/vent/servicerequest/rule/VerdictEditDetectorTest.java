package vent.servicerequest.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.servicerequest.domain.VerdictContent;
import vent.servicerequest.domain.type.VerdictField;

/**
 * 판정이 AI 소견을 고쳤는지 비교 단위 테스트.
 *
 * <p>테스트 지점: 비교 클래스의 공개 메서드. 기준은 #11에서 정한 것이다. 화면이 보낸 표시를 믿지 않고 서버가 항목별로
 * 비교한다. 앞뒤 공백만 다른 것은 고친 것으로 보지 않는다. 원인 순서(가능성 순)가 바뀌면 고친 것이고, 부품·조치 순서만
 * 바뀐 것은 고친 것이 아니다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
class VerdictEditDetectorTest {

    private static final VerdictContent AI = new VerdictContent(
            List.of("필터 막힘으로 급기 부족"), List.of("필터 교체", "교체 후 풍량 재측정"),
            List.of("DOMEKT R 300V 교체 필터 세트"), false, List.of(), "최근 필터가 많이 막혀 있어요.");

    private final VerdictEditDetector detector = new VerdictEditDetector();

    @Test
    @DisplayName("AI 소견과 같은 내용이면 고친 항목이 없다 (앞뒤 공백 차이는 무시)")
    void sameContentIsNotEdited() {
        VerdictContent submitted = new VerdictContent(
                List.of(" 필터 막힘으로 급기 부족 "), List.of("필터 교체", "교체 후 풍량 재측정"),
                List.of("DOMEKT R 300V 교체 필터 세트"), false, List.of(), "최근 필터가 많이 막혀 있어요.  ");

        assertThat(detector.editedFields(submitted, AI)).isEmpty();
    }

    @Test
    @DisplayName("원인, 부품, 방문 필요, 고객 안내문을 바꾸면 바꾼 항목을 순서대로 돌려준다")
    void changedFieldsAreListed() {
        VerdictContent submitted = new VerdictContent(
                List.of("필터 막힘으로 급기 부족", "덕트 일부 막힘"), List.of("필터 교체", "교체 후 풍량 재측정"),
                List.of(), true, List.of(), "필터 교체 일정을 잡아 드릴게요.");

        assertThat(detector.editedFields(submitted, AI)).containsExactly(
                VerdictField.CAUSES, VerdictField.PARTS, VerdictField.NEEDS_VISIT, VerdictField.CUSTOMER_REPLY);
    }

    @Test
    @DisplayName("원인 순서가 바뀌면 고친 것이다 (순서가 가능성 순위)")
    void causeOrderMatters() {
        VerdictContent ai = new VerdictContent(List.of("A", "B"), List.of(), List.of(), false, List.of(), "안내");
        VerdictContent submitted = new VerdictContent(List.of("B", "A"), List.of(), List.of(), false, List.of(), "안내");

        assertThat(detector.editedFields(submitted, ai)).containsExactly(VerdictField.CAUSES);
    }

    @Test
    @DisplayName("부품·조치·권하는 점검은 순서만 바뀐 것은 고친 것이 아니다 (체크를 껐다 켠 경우)")
    void itemOrderDoesNotMatter() {
        VerdictContent ai = new VerdictContent(List.of("A"), List.of("교체", "재측정"), List.of("필터", "케이블"), false,
                List.of("TAB", "블로어도어 테스트"), "안내");
        VerdictContent submitted = new VerdictContent(List.of("A"), List.of("재측정", "교체"), List.of("케이블", "필터"), false,
                List.of("블로어도어 테스트", "TAB"), "안내");

        assertThat(detector.editedFields(submitted, ai)).isEmpty();
    }
}
