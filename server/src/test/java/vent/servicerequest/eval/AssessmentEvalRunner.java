package vent.servicerequest.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.boot.convert.DurationStyle;
import org.yaml.snakeyaml.Yaml;
import vent.servicerequest.client.AssessmentClient;
import vent.servicerequest.client.AssessmentClientException;
import vent.servicerequest.client.AssessmentInput;
import vent.servicerequest.client.AssessmentPrompt;
import vent.servicerequest.client.AssessmentReply;
import vent.servicerequest.client.AssessmentUsage;
import vent.servicerequest.client.AssessmentClientConfiguration;
import vent.servicerequest.client.ClaudeAssessmentProperties;
import vent.servicerequest.client.FakeAssessmentClient;
import vent.servicerequest.client.FakeAssessmentMode;
import vent.servicerequest.client.FakeAssessmentProperties;
import vent.servicerequest.rule.AssessmentValidation;
import vent.servicerequest.rule.AssessmentValidator;
import vent.servicerequest.rule.PersonalInfoMasker;
import vent.servicerequest.rule.SituationSummary;
import vent.servicerequest.service.AssessmentInputFactory;
import vent.servicerequest.service.AssessmentProperties;

/**
 * AI 소견 평가 세트 실행기 (스펙 8.7, #12).
 *
 * <p>고정된 상황 요약 5개({@code src/test/resources/eval/*.json})로 AI 소견을 받아 1순위 원인 분류가 정답인지 채점하고
 * {@code docs/eval/}에 보고서를 쓴다. 운영과 같은 경로(입력 만들기 → AI → 검증기, 검증 실패 시 재시도 모델로 다시)를
 * 쓰되 DB는 쓰지 않는다. 설정(판매 부품, 시도 수, Claude 시간 제한·재시도·서버 재실행)은 운영과 같은 {@code application.yml}에서
 * 읽고, 모델·effort만 명령 인자로 바꾼다. Claude 호출부도 앱과 같은 방법으로 만든다. 시나리오마다 여러 번 돌려 답이
 * 흔들리는지도 본다. 시도 순서는 {@code AssessmentService}와 같게 유지한다(DB 저장이 섞여 있어 공유하지 않는다).
 *
 * <pre>
 * ./gradlew :server:evalAssessment                                  # Claude, 기본 설정 (ANTHROPIC_API_KEY 필요, 비용 발생)
 * ./gradlew :server:evalAssessment -Peval.client=fake               # 가짜 AI로 채점 도구 점검 (5/5가 정상)
 * ./gradlew :server:evalAssessment -Peval.effort=low -Peval.label=opus-low
 * ./gradlew :server:evalAssessment -Peval.model=claude-haiku-5-5 -Peval.retryModel=claude-opus-5-5 -Peval.label=haiku-to-opus
 * </pre>
 *
 * <p>비용은 응답의 토큰 수에 모델별 단가(아래 표, 2026-10 기준)를 곱한 추정치다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public final class AssessmentEvalRunner {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<String> FIXTURES = List.of("1-filter-clogged", "2-too-many-occupants", "3-vemc-not-linked",
            "4-normal-but-stuffy", "5-gateway-offline");
    /** 100만 토큰당 달러: 입력, 출력, 캐시 읽기, 캐시 쓰기 */
    private static final Map<String, double[]> PRICES = Map.of(
            "claude-opus-5-5", new double[] {4.00, 20.00, 0.20, 5.00},
            "claude-sonnet-5-5", new double[] {2.00, 10.00, 0.20, 2.50},
            "claude-haiku-5-5", new double[] {0.10, 0.50, 0.01, 0.125});

    private AssessmentEvalRunner() {
    }

    public static void main(String[] args) throws Exception {
        String clientName = System.getProperty("eval.client", "claude");
        Map<String, Object> assessmentYaml = assessmentYaml();
        Map<String, Object> claudeYaml = section(assessmentYaml, "claude");
        String model = System.getProperty("eval.model", (String) claudeYaml.get("model"));
        String retryModel = System.getProperty("eval.retryModel", model);
        String effort = System.getProperty("eval.effort", (String) claudeYaml.get("effort"));
        int repeats = Integer.parseInt(System.getProperty("eval.repeats", "3"));
        String label = System.getProperty("eval.label", clientName.equals("fake") ? "fake" : model + "-" + effort);
        Path outDir = Path.of(System.getProperty("eval.out", "docs/eval"));

        @SuppressWarnings("unchecked")
        List<String> parts = List.copyOf((List<String>) assessmentYaml.get("parts"));
        AssessmentProperties assessmentProperties = new AssessmentProperties(Duration.ofDays(7), Duration.ofSeconds(30),
                (Integer) assessmentYaml.get("max-attempts"), duration(assessmentYaml, "call-timeout"),
                duration(assessmentYaml, "pending-after"), parts);
        AssessmentClient client = clientName.equals("fake")
                ? new FakeAssessmentClient(new FakeAssessmentProperties(FakeAssessmentMode.NORMAL))
                : claude(new ClaudeAssessmentProperties(model, retryModel, effort,
                        ((Number) claudeYaml.get("max-tokens")).longValue(), duration(claudeYaml, "timeout"),
                        (Integer) claudeYaml.get("max-retries"), (Boolean) claudeYaml.get("server-fallback"), null),
                        assessmentProperties);
        AssessmentInputFactory inputFactory = new AssessmentInputFactory(new PersonalInfoMasker(), assessmentProperties);
        AssessmentValidator validator = new AssessmentValidator();

        List<Run> runs = new ArrayList<>();
        for (String id : FIXTURES) {
            JsonNode fixture = readFixture(id);
            SituationSummary summary = JSON.treeToValue(fixture.path("summary"), SituationSummary.class);
            List<String> expected = JSON.convertValue(fixture.path("expected"), JSON.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
            for (int i = 1; i <= repeats; i++) {
                Run run = runOnce(id, fixture.path("scenario").asText(), expected, summary,
                        fixture.path("customerMessage").asText(), client, inputFactory, validator, assessmentProperties);
                runs.add(run);
                System.out.printf("%s #%d: %s (%s, %d번 시도, %.1f초)%n", id, i, run.correct() ? "맞음" : "틀림",
                        run.category(), run.attempts(), run.millis() / 1000.0);
            }
        }

        String report = report(label, clientName, model, retryModel, effort, repeats, runs);
        Files.createDirectories(outDir);
        Path file = reportFile(outDir, LocalDate.now(ZoneId.of("Asia/Seoul")) + "-" + label);
        Files.writeString(file, report, StandardCharsets.UTF_8);
        System.out.println();
        System.out.println(summaryLine(label, clientName.equals("fake") ? "가짜 AI" : model + " → " + retryModel,
                clientName.equals("fake") ? "-" : effort, runs));
        System.out.println("보고서: " + file);
    }

    /** 같은 날 같은 구성을 다시 돌려도 앞 보고서를 덮어쓰지 않도록 -2, -3 …을 붙인다(답이 흔들리는 기록도 남긴다). */
    private static Path reportFile(Path outDir, String name) {
        Path file = outDir.resolve(name + ".md");
        for (int n = 2; Files.exists(file); n++) {
            file = outDir.resolve(name + "-" + n + ".md");
        }
        return file;
    }

    /** 운영과 같은 순서: 입력 만들기 → AI → 검증, 실패하면 재시도 모델로 한 번 더. */
    private static Run runOnce(String id, String scenario, List<String> expected, SituationSummary summary, String message,
                               AssessmentClient client, AssessmentInputFactory inputFactory, AssessmentValidator validator,
                               AssessmentProperties properties) {
        long started = System.nanoTime();
        List<String> models = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        AssessmentUsage usage = AssessmentUsage.NONE;
        Double cost = 0.0;
        String category = "-";
        boolean passed = false;
        boolean worthRetrying = true;
        int attempt = 0;
        while (attempt < properties.maxAttempts() && !passed && worthRetrying) {
            attempt++;
            category = "-";
            AssessmentInput input = inputFactory.create(summary, message, attempt);
            try {
                AssessmentReply reply = client.assess(input);
                models.add(reply.model());
                usage = add(usage, reply.usage());
                Double attemptCost = costOf(reply.model(), reply.usage());
                cost = cost == null || attemptCost == null ? null : cost + attemptCost;
                AssessmentValidation validation = validator.validate(reply.rawText(), summary.availableMetricKeys(),
                        properties.parts());
                passed = validation.passed();
                if (validation.parsed() != null) {
                    category = validation.parsed().at("/causes/0/category").asText("-");
                }
                validation.errors().forEach(e -> errors.add(e.code() + ": " + e.message()));
            } catch (AssessmentClientException e) {
                // 거절·잘림도 과금될 수 있어 사용량을 더한다
                models.add(e.model() == null ? "-" : e.model());
                usage = add(usage, e.usage());
                Double attemptCost = e.model() == null ? 0.0 : costOf(e.model(), e.usage());
                cost = cost == null || attemptCost == null ? null : cost + attemptCost;
                errors.add(e.code() + ": " + e.getMessage());
                worthRetrying = e.retryable();
            } catch (RuntimeException e) {
                // 운영과 같이 다른 오류도 이 실행의 실패로만 남기고 평가는 계속한다
                models.add("-");
                errors.add(AssessmentClientException.CLIENT_ERROR + ": " + e);
            }
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        boolean correct = passed && expected.contains(category);
        return new Run(id, scenario, expected, category, passed, correct, attempt, millis, usage, cost, models, errors);
    }

    private static AssessmentClient claude(ClaudeAssessmentProperties properties, AssessmentProperties assessmentProperties) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ANTHROPIC_API_KEY가 없습니다. 실제 평가는 키가 필요합니다. 채점 도구만 보려면 -Peval.client=fake");
            System.exit(1);
        }
        return AssessmentClientConfiguration.newClaudeClient(apiKey, properties, assessmentProperties, new AssessmentPrompt());
    }

    private static String report(String label, String clientName, String model, String retryModel, String effort, int repeats,
                                 List<Run> runs) {
        StringBuilder md = new StringBuilder();
        md.append("# AI 소견 평가: ").append(label).append("\n\n");
        md.append("<!-- 평가 세트 실행 결과 (스펙 8.7, #12). AssessmentEvalRunner가 만든다. -->\n\n");
        md.append("| 항목 | 값 |\n|---|---|\n");
        md.append("| 날짜 | ").append(LocalDate.now(ZoneId.of("Asia/Seoul"))).append(" |\n");
        md.append("| AI | ").append(clientName.equals("fake") ? "가짜 AI (채점 도구 점검)" : "Claude").append(" |\n");
        if (!clientName.equals("fake")) {
            md.append("| 1차 모델 / 재시도 모델 | ").append(model).append(" / ").append(retryModel).append(" |\n");
            md.append("| effort | ").append(effort).append(" |\n");
        }
        md.append("| 프롬프트 | ").append(AssessmentPrompt.VERSION).append(" |\n");
        md.append("| 반복 | 시나리오마다 ").append(repeats).append("번 |\n\n");

        md.append("## 결과\n\n").append(totals(runs)).append("\n\n");
        md.append("## 시나리오별\n\n");
        md.append("| 시나리오 | 정답 | 맞음 | 검증 통과 | 1순위 분류 | 평균 시간 |\n|---|---|---|---|---|---|\n");
        Map<String, List<Run>> byScenario = runs.stream()
                .collect(Collectors.groupingBy(Run::id, LinkedHashMap::new, Collectors.toList()));
        byScenario.forEach((id, list) -> {
            Map<String, Long> categories = list.stream().collect(Collectors.groupingBy(Run::category, TreeMap::new, Collectors.counting()));
            md.append("| ").append(list.get(0).scenario())
                    .append(" | ").append(String.join(" 또는 ", list.get(0).expected()))
                    .append(" | ").append(count(list, Run::correct)).append("/").append(list.size())
                    .append(" | ").append(count(list, Run::passed)).append("/").append(list.size())
                    .append(" | ").append(categories.entrySet().stream().map(e -> e.getKey() + " ×" + e.getValue())
                            .collect(Collectors.joining(", ")))
                    .append(" | ").append(String.format("%.1f초", list.stream().mapToLong(Run::millis).average().orElse(0) / 1000))
                    .append(" |\n");
        });
        // 결국 맞았어도 재시도한 실행은 첫 시도에서 걸린 이유를 남긴다(검증기가 실제로 잡은 사례)
        List<Run> flaggedRuns = runs.stream().filter(r -> !r.correct() || r.retried()).toList();
        if (!flaggedRuns.isEmpty()) {
            md.append("\n## 틀리거나 검증에 걸린 실행\n\n");
            flaggedRuns.forEach(r -> md.append("- ").append(r.id()).append(": 1순위 ").append(r.category())
                    .append(", 시도 ").append(r.attempts()).append("번 (").append(String.join(" → ", r.models())).append(")")
                    .append(r.errors().isEmpty() ? "" : " · " + String.join(" / ", r.errors())).append("\n"));
        }
        return md.toString();
    }

    private static String totals(List<Run> runs) {
        AssessmentUsage usage = runs.stream().map(Run::usage).reduce(AssessmentUsage.NONE, AssessmentEvalRunner::add);
        String cost = cost(runs);
        return String.format("- 1순위 원인 정답률: **%d/%d** (%.0f%%)%n- 검증 통과율: %d/%d%n- 재시도한 실행: %d%n"
                        + "- 응답 시간: 평균 %.1f초, 최대 %.1f초%n- 토큰: 입력 %,d · 출력 %,d · 캐시 읽기 %,d · 캐시 쓰기 %,d%n- 추정 비용: %s",
                count(runs, Run::correct), runs.size(), 100.0 * count(runs, Run::correct) / runs.size(),
                count(runs, Run::passed), runs.size(), runs.stream().filter(Run::retried).count(),
                runs.stream().mapToLong(Run::millis).average().orElse(0) / 1000,
                runs.stream().mapToLong(Run::millis).max().orElse(0) / 1000.0,
                usage.inputTokens(), usage.outputTokens(), usage.cacheReadTokens(), usage.cacheWriteTokens(), cost);
    }

    /** README에 붙일 한 줄. */
    private static String summaryLine(String label, String models, String effort, List<Run> runs) {
        return String.format("| %s | %s | %s | %s | %s | %d/%d | %d/%d | %.1f초 | %s |", LocalDate.now(ZoneId.of("Asia/Seoul")),
                label, models, effort, AssessmentPrompt.VERSION, count(runs, Run::correct), runs.size(), count(runs, Run::passed), runs.size(),
                runs.stream().mapToLong(Run::millis).average().orElse(0) / 1000, cost(runs));
    }

    /** 실행마다 시도별로 그 시도의 모델 단가를 곱해 더한 값. 단가를 모르는 모델이 있으면 알 수 없음. */
    private static String cost(List<Run> runs) {
        if (runs.stream().anyMatch(r -> r.cost() == null)) {
            return "알 수 없음 (단가 표에 없는 모델)";
        }
        return String.format("$%.3f", runs.stream().mapToDouble(Run::cost).sum());
    }

    /** 시도 한 번의 추정 비용(달러). 가짜 AI는 0, 단가를 모르는 모델이면 null. */
    private static Double costOf(String model, AssessmentUsage usage) {
        if (usage.equals(AssessmentUsage.NONE)) {
            return 0.0;
        }
        double[] price = PRICES.entrySet().stream().filter(e -> model.startsWith(e.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (price == null) {
            return null;
        }
        return (usage.inputTokens() * price[0] + usage.outputTokens() * price[1] + usage.cacheReadTokens() * price[2]
                + usage.cacheWriteTokens() * price[3]) / 1_000_000;
    }

    private static AssessmentUsage add(AssessmentUsage a, AssessmentUsage b) {
        return new AssessmentUsage(a.inputTokens() + b.inputTokens(), a.outputTokens() + b.outputTokens(),
                a.cacheReadTokens() + b.cacheReadTokens(), a.cacheWriteTokens() + b.cacheWriteTokens());
    }

    private static long count(List<Run> runs, java.util.function.Predicate<Run> condition) {
        return runs.stream().filter(condition).count();
    }

    private static JsonNode readFixture(String id) throws IOException {
        try (InputStream in = AssessmentEvalRunner.class.getClassLoader().getResourceAsStream("eval/" + id + ".json")) {
            if (in == null) {
                throw new IllegalStateException("평가 시나리오 파일 없음: eval/" + id + ".json");
            }
            return JSON.readTree(in);
        }
    }

    /** 운영과 같은 AI 소견 설정({@code vent.assessment})을 application.yml에서 읽는다. */
    private static Map<String, Object> assessmentYaml() throws IOException {
        try (InputStream in = AssessmentEvalRunner.class.getClassLoader().getResourceAsStream("application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            return section(section(root, "vent"), "assessment");
        }
    }

    /** application.yml의 기간 값(예: 3m, 60s)을 Spring과 같은 방식으로 읽는다. */
    private static Duration duration(Map<String, Object> section, String key) {
        return DurationStyle.detectAndParse(String.valueOf(section.get(key)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    /**
     * 평가 실행 한 번의 결과.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#12)
     */
    private record Run(String id, String scenario, List<String> expected, String category, boolean passed, boolean correct,
                       int attempts, long millis, AssessmentUsage usage, Double cost, List<String> models,
                       List<String> errors) {

        /** 첫 시도가 통과하지 못해 다시 시도했는가. */
        boolean retried() {
            return attempts > 1;
        }
    }
}
