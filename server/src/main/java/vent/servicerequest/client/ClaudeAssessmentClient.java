package vent.servicerequest.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 실제 Claude AI 소견 (스펙 8.3~8.6, #12, ADR-0016).
 *
 * <p>Anthropic 공식 Java SDK로 Messages API를 부른다.
 * <ul>
 *   <li>시스템 프롬프트(역할, 기준표, 부품 목록)는 요청마다 같아 프롬프트 캐싱을 건다</li>
 *   <li>구조화 출력으로 정해진 JSON 형식만 받는다({@link AssessmentOutputSchema}). 의미 검증은 검증기가 한다</li>
 *   <li>1차 시도는 {@code model}, 재시도는 {@code retry-model}로 부른다(단계적 승격)</li>
 *   <li>안전 분류기가 거절하면 서버가 다른 모델로 다시 실행한다({@code fallbacks: "default"}, 베타). 이 기능이 있는
 *       모델에만 보낸다. 실제로 답한 모델은 응답에서 읽어 기록한다</li>
 * </ul>
 * 시간 초과·연결 실패 등은 {@code CLIENT_ERROR}, 거절은 {@code REFUSAL}, 길이 한도에 걸린 응답은 {@code TRUNCATED}로
 * 던진다. 다시 해 볼 만한 실패면 받는 쪽이 다음 시도로 넘기고, 끝까지 실패하면 사람 확인 필요가 된다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public class ClaudeAssessmentClient implements AssessmentClient {

    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    static final List<String> SERVER_FALLBACK_MODELS = List.of("claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5",
            "claude-fable-5-1");
    static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    private final AnthropicClient anthropic;
    private final ClaudeAssessmentProperties properties;
    private final AssessmentPrompt prompt;
    private final OutputConfig.Effort effort;

    public ClaudeAssessmentClient(AnthropicClient anthropic, ClaudeAssessmentProperties properties, AssessmentPrompt prompt) {
        this.anthropic = anthropic;
        this.properties = properties;
        this.prompt = prompt;
        if (!EFFORTS.contains(properties.effort())) {
            throw new IllegalStateException("vent.assessment.claude.effort는 " + EFFORTS + " 중 하나: " + properties.effort());
        }
        this.effort = OutputConfig.Effort.of(properties.effort());
    }

    @Override
    public AssessmentReply assess(AssessmentInput input) {
        MessageCreateParams request = request(input);
        try {
            Message message = anthropic.messages().create(request);
            // 응답 필드는 읽을 때 검사되므로(AnthropicInvalidDataException) 읽기까지 이 안에서 한다
            String model = message.model().asString();
            AssessmentUsage usage = usage(message.usage());
            StopReason stopReason = message.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stopReason)) {
                String category = message.stopDetails().flatMap(d -> d.category().map(Object::toString)).orElse("알 수 없음");
                throw new AssessmentClientException(AssessmentClientException.REFUSAL,
                        "AI가 답하지 않음 (안전 거절: " + category + ")", false, model, usage, null);
            }
            if (StopReason.MAX_TOKENS.equals(stopReason)) {
                throw new AssessmentClientException(AssessmentClientException.TRUNCATED,
                        "AI 응답이 길이 한도(" + properties.maxTokens() + "토큰)에 걸려 잘림", true, model, usage, null);
            }
            String text = message.content().stream()
                    .map(ContentBlock::text)
                    .flatMap(Optional::stream)
                    .map(block -> block.text())
                    .collect(Collectors.joining());
            return new AssessmentReply(model, text, usage);
        } catch (AnthropicException e) {
            throw new AssessmentClientException(AssessmentClientException.CLIENT_ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), isRetryable(e), null, AssessmentUsage.NONE, e);
        }
    }

    /** 다시 해도 같은 결과일 오류(잘못된 요청, 인증·권한, 없는 모델)는 다음 시도를 하지 않는다. */
    private static boolean isRetryable(AnthropicException e) {
        return !(e instanceof BadRequestException || e instanceof UnauthorizedException
                || e instanceof PermissionDeniedException || e instanceof NotFoundException
                || e instanceof UnprocessableEntityException);
    }

    /** 요청 만들기. 시스템 프롬프트와 스키마는 요청마다 같다. */
    MessageCreateParams request(AssessmentInput input) {
        String model = input.attempt() <= 1 ? properties.model() : properties.retryModel();
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(properties.maxTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(prompt.system(input.parts()))
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(prompt.user(input))
                .outputConfig(OutputConfig.builder()
                        .effort(effort)
                        .format(JsonOutputFormat.builder().schema(schema(input.parts())).build())
                        .build());
        if (properties.serverFallback() && supportsServerFallback(model)) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA);
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return builder.build();
    }

    /** 서버 재실행은 Opus 5.5·Opus 5·Sonnet 5.5·Fable 5.1에만 있다. Haiku 5.5 등에는 보내지 않는다. */
    static boolean supportsServerFallback(String model) {
        return SERVER_FALLBACK_MODELS.stream().anyMatch(model::startsWith);
    }

    private static JsonOutputFormat.Schema schema(List<String> parts) {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> entry : AssessmentOutputSchema.of(parts).entrySet()) {
            schema.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        return schema.build();
    }

    private static AssessmentUsage usage(Usage usage) {
        return new AssessmentUsage(usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }
}
