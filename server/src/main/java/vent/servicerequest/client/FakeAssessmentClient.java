package vent.servicerequest.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vent.servicerequest.rule.AssessmentCauseCategory;
import vent.servicerequest.rule.SituationSummary;

/**
 * 가짜 AI 소견 (스펙 8.6, #10).
 *
 * <p>API 키 없이 시연하려고 상황 요약을 보고 시나리오별 고정 응답을 낸다(스펙 8.7의 5가지). 위에서부터 먼저 맞는 것.
 * <ol>
 *   <li>데이터 충분도 30% 미만 → 데이터 부족으로 판단 보류</li>
 *   <li>후드 연동 성공률 50% 미만 → VEMC 미연동</li>
 *   <li>필터 차압이 30Pa 넘게 올랐거나 130Pa 넘음 → 필터 막힘</li>
 *   <li>CO2 평균 1,000ppm 넘는데 급기는 목표의 80% 넘게 나옴 → 인원 대비 목표 풍량 부족(TAB 권유)</li>
 *   <li>급기가 목표의 80% 미만 → 풍량 부족</li>
 *   <li>그 밖 → 장비 데이터는 정상, 방문 상담 또는 기밀 점검 권유</li>
 * </ol>
 * 검증을 시연하려고 일부러 틀리는 모드도 있다({@link FakeAssessmentMode}). 테스트는 실행 중에
 * {@link #useMode(FakeAssessmentMode)}로 바꾼다. 어느 구현을 쓸지는 {@link AssessmentClientConfiguration}이 고른다(#12).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public class FakeAssessmentClient implements AssessmentClient {

    static final String MODEL = "fake-assessment-1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private volatile FakeAssessmentMode mode;

    public FakeAssessmentClient(FakeAssessmentProperties properties) {
        this.mode = properties.mode();
    }

    /** 응답 방식을 바꾼다 (시연·테스트용). */
    public void useMode(FakeAssessmentMode mode) {
        this.mode = mode;
    }

    @Override
    public AssessmentReply assess(AssessmentInput input) {
        Map<String, Object> reply = scenario(input.summary(), input.parts());
        FakeAssessmentMode current = mode == FakeAssessmentMode.FLAKY
                ? (input.attempt() == 1 ? FakeAssessmentMode.BAD_EVIDENCE : FakeAssessmentMode.NORMAL)
                : mode;
        switch (current) {
            case BAD_PART -> reply.put("parts", List.of("만능 필터 X"));
            case BAD_EVIDENCE -> firstCause(reply).put("evidence", List.of("humidityTrend"));
            case FORBIDDEN_PHRASE -> reply.put("customerReply", reply.get("customerReply") + " 이번에는 무상으로 교체해 드릴게요.");
            case BROKEN_JSON -> {
                return new AssessmentReply(MODEL, "{\"causes\": [ {\"title\": \"필터 막힘\"", AssessmentUsage.NONE);
            }
            case UNAVAILABLE -> throw new IllegalStateException("가짜 AI: 응답 없음");
            case REFUSAL -> throw new AssessmentClientException(AssessmentClientException.REFUSAL,
                    "가짜 AI: 안전 거절", false, MODEL, AssessmentUsage.NONE, null);
            case NORMAL, FLAKY -> {
                // 그대로
            }
        }
        try {
            return new AssessmentReply(MODEL, JSON.writeValueAsString(reply), AssessmentUsage.NONE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("가짜 소견을 만들 수 없음", e);
        }
    }

    private static Map<String, Object> scenario(SituationSummary s, List<String> catalog) {
        String model = s.site().ventilatorModel();
        if (s.dataCoverage() < 0.3) {
            return reply(cause("최근 데이터가 부족해 원인을 판단하기 어려움", "MEDIUM", AssessmentCauseCategory.INSUFFICIENT_DATA, "dataCoverage", "offlineCount"),
                    List.of("게이트웨이 연결과 현장 Wi-Fi 확인", "연결 후 데이터를 모아 다시 확인"), List.of(), true, List.of(),
                    "최근 장비 데이터가 충분히 들어오지 않아 원인을 바로 판단하기 어려워요. 담당자가 연결 상태를 먼저 확인할게요.");
        }
        if (s.hoodInterlockSuccessRate() != null && s.hoodInterlockSuccessRate() < 0.5) {
            return reply(cause("VEMC 미연동으로 후드 사용 시 급기가 오르지 않음", "HIGH", AssessmentCauseCategory.VEMC_NOT_LINKED, "hoodInterlockSuccessRate"),
                    List.of("VEMC와 환기장치 사이 신호 배선 점검", "후드 ON 시 급기 상승 확인"), parts(catalog, "VEMC 신호 케이블"), true,
                    List.of(), "후드를 켰을 때 환기장치가 함께 움직이지 않는 것으로 보여요. 담당자가 방문해 연결을 점검할게요.");
        }
        if (s.filterPaTrend() != null && (s.filterPaTrend().changePa() > 30 || s.filterPaTrend().latestPa() > 130)) {
            return reply(cause("필터 막힘으로 급기 부족", "HIGH", AssessmentCauseCategory.FILTER_CLOGGED, "filterPaTrend", "supplyToTargetRatio"),
                    List.of("필터 교체", "교체 후 풍량 재측정"), parts(catalog, model + " 교체 필터 세트"), false, List.of(),
                    "최근 필터가 많이 막혀 공기가 충분히 들어오지 않고 있어요. 필터 교체 안내를 도와드릴게요.");
        }
        if (s.co2AvgPpm() != null && s.co2AvgPpm() > 1000 && s.supplyToTargetRatio() != null && s.supplyToTargetRatio() >= 0.8) {
            return reply(cause("재실 인원에 비해 목표 풍량이 부족함", "MEDIUM", AssessmentCauseCategory.TARGET_AIRFLOW_MISMATCH, "co2AvgPpm", "supplyToTargetRatio"),
                    List.of("현재 재실 인원 확인", "목표 풍량 재산정"), List.of(), true, List.of("TAB"),
                    "장비는 정상인데 실내 인원에 비해 환기량이 부족해 보여요. 풍량을 다시 맞추는 점검을 안내해 드릴게요.");
        }
        if (s.supplyToTargetRatio() != null && s.supplyToTargetRatio() < 0.8) {
            return reply(cause("급기가 목표 풍량보다 부족함", "MEDIUM", AssessmentCauseCategory.AIRFLOW_SHORTAGE, "supplyToTargetRatio"),
                    List.of("팬 설정과 덕트 막힘 점검"), List.of(), true, List.of("TAB"),
                    "환기장치가 내보내는 공기가 목표보다 적어요. 담당자가 설정과 덕트를 점검할게요.");
        }
        return reply(cause("장비 데이터는 정상으로 보임", "LOW", AssessmentCauseCategory.NO_EQUIPMENT_PROBLEM, "co2AvgPpm", "supplyToTargetRatio"),
                List.of("방문 상담", "기밀 점검 권유"), List.of(), true, List.of("블로어도어 테스트"),
                "장비 데이터는 정상이에요. 불편하신 점을 직접 확인하도록 방문 상담을 안내해 드릴게요.");
    }

    /** 판매 목록에 있는 부품만 넣는다. */
    private static List<String> parts(List<String> catalog, String part) {
        return catalog.contains(part) ? List.of(part) : List.of();
    }

    private static Map<String, Object> cause(String title, String likelihood, AssessmentCauseCategory category,
                                             String... evidence) {
        Map<String, Object> cause = new LinkedHashMap<>();
        cause.put("title", title);
        cause.put("likelihood", likelihood);
        cause.put("category", category.name());
        cause.put("evidence", List.of(evidence));
        return cause;
    }

    private static Map<String, Object> reply(Map<String, Object> cause, List<String> actions, List<String> parts,
                                             boolean needsVisit, List<String> services, String customerReply) {
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("causes", new ArrayList<>(List.of(cause)));
        reply.put("technicianActions", actions);
        reply.put("parts", parts);
        reply.put("needsVisit", needsVisit);
        reply.put("recommendedServices", services);
        reply.put("customerReply", customerReply);
        return reply;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCause(Map<String, Object> reply) {
        return ((List<Map<String, Object>>) reply.get("causes")).get(0);
    }
}
