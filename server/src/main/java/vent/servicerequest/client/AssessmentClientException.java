package vent.servicerequest.client;

/**
 * AI를 불렀지만 쓸 수 있는 소견을 받지 못함 (#12). 코드가 그대로 검증 오류 코드로 남는다.
 *
 * <ul>
 *   <li>{@code CLIENT_ERROR}: 시간 초과, 연결 실패, 인증 실패, 요청 한도 초과 등으로 AI를 부를 수 없음</li>
 *   <li>{@code REFUSAL}: AI의 안전 분류기가 답하지 않음</li>
 *   <li>{@code TRUNCATED}: 응답 길이 한도에 걸려 잘림</li>
 * </ul>
 * 다시 해도 같은 결과가 나올 실패(안전 거절, 잘못된 요청, 인증·권한 실패)는 {@code retryable}이 false라 다음 시도를 하지
 * 않는다. 안전 거절은 서버가 이미 다른 모델로 다시 실행해 본 결과다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public class AssessmentClientException extends RuntimeException {

    public static final String CLIENT_ERROR = "CLIENT_ERROR";
    public static final String REFUSAL = "REFUSAL";
    public static final String TRUNCATED = "TRUNCATED";

    private final String code;
    private final boolean retryable;
    private final String model;
    private final AssessmentUsage usage;

    /**
     * @param model 응답한 모델. 응답이 없었으면 null
     * @param usage 응답이 왔다면 그 토큰 사용량(거절·잘림도 과금될 수 있다). 없으면 {@link AssessmentUsage#NONE}
     */
    public AssessmentClientException(String code, String message, boolean retryable, String model, AssessmentUsage usage,
                                     Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
        this.model = model;
        this.usage = usage;
    }

    public String code() {
        return code;
    }

    /** 다음 시도를 해 볼 만한지. */
    public boolean retryable() {
        return retryable;
    }

    public String model() {
        return model;
    }

    public AssessmentUsage usage() {
        return usage;
    }
}
