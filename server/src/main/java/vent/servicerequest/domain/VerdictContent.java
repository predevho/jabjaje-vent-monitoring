package vent.servicerequest.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 판정 내용 (#11). AI 소견과 같은 항목이라 둘을 항목별로 비교할 수 있다. 판정 엔티티의 값 타입이다.
 *
 * @param causes              확정한 원인 (가능성 순)
 * @param technicianActions   기사가 할 조치
 * @param parts               필요한 부품 (판매 목록에 있는 이름)
 * @param needsVisit          방문이 필요한지
 * @param recommendedServices 권하는 점검 (예: TAB, 블로어도어 테스트)
 * @param customerReply       고객에게 보낼 안내
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public record VerdictContent(List<String> causes, List<String> technicianActions, List<String> parts, boolean needsVisit,
                             List<String> recommendedServices, String customerReply) {

    public VerdictContent {
        causes = copy(causes);
        technicianActions = copy(technicianActions);
        parts = copy(parts);
        recommendedServices = copy(recommendedServices);
    }

    /** 검증을 통과한 AI 소견 JSON에서 같은 항목을 꺼낸다. 원인은 제목만 쓴다. */
    public static VerdictContent fromAssessment(JsonNode result) {
        List<String> causes = new ArrayList<>();
        result.path("causes").forEach(cause -> causes.add(cause.path("title").asText()));
        return new VerdictContent(causes, texts(result.path("technicianActions")), texts(result.path("parts")),
                result.path("needsVisit").asBoolean(false), texts(result.path("recommendedServices")),
                result.path("customerReply").asText(""));
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : values.stream().map(v -> v == null ? "" : v).toList();
    }
}
