package vent.servicerequest.client;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vent.servicerequest.rule.AssessmentCauseCategory;
import vent.servicerequest.rule.SituationSummary;

/**
 * AI 소견 출력 JSON 스키마 (스펙 8.4, #12).
 *
 * <p>구조화 출력으로 API가 이 형식의 JSON만 만들게 한다. 가능성·분류·근거 지표·부품·권하는 점검은 정해진 값만 허용한다.
 * 스키마는 판매 부품 목록에만 의존하므로 앱이 떠 있는 동안 바뀌지 않는다(같은 스키마면 API가 준비 작업을 재사용한다).
 * "값이 있는 지표만 근거로", "금지 표현 없음"은 스키마로 표현할 수 없어 검증기가 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public final class AssessmentOutputSchema {

    static final List<String> RECOMMENDED_SERVICES = List.of("TAB", "블로어도어 테스트");

    private AssessmentOutputSchema() {
    }

    /** JSON 스키마 (Map으로 표현). */
    public static Map<String, Object> of(List<String> parts) {
        Map<String, Object> cause = object(Map.of(
                "title", type("string"),
                "likelihood", enumOf(List.of("HIGH", "MEDIUM", "LOW")),
                "category", enumOf(Arrays.stream(AssessmentCauseCategory.values()).map(Enum::name).toList()),
                "evidence", arrayOf(enumOf(SituationSummary.METRIC_KEYS))),
                List.of("title", "likelihood", "category", "evidence"));
        return object(Map.of(
                "causes", arrayOf(cause),
                "technicianActions", arrayOf(type("string")),
                "parts", arrayOf(parts.isEmpty() ? type("string") : enumOf(parts)),
                "needsVisit", type("boolean"),
                "recommendedServices", arrayOf(enumOf(RECOMMENDED_SERVICES)),
                "customerReply", type("string")),
                List.of("causes", "technicianActions", "parts", "needsVisit", "recommendedServices", "customerReply"));
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = type("object");
        // 키 순서를 필수 목록 순서로 고정해 요청마다 같은 스키마가 되게 한다
        Map<String, Object> ordered = new LinkedHashMap<>();
        required.forEach(key -> ordered.put(key, properties.get(key)));
        schema.put("properties", ordered);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    // Map.of는 꺼낼 때 순서가 실행마다 달라질 수 있어, 순서가 고정된 LinkedHashMap만 쓴다
    private static Map<String, Object> type(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        return schema;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        Map<String, Object> schema = type("array");
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> enumOf(List<String> values) {
        Map<String, Object> schema = type("string");
        schema.put("enum", values);
        return schema;
    }
}
