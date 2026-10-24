package vent.servicerequest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vent.servicerequest.client.AssessmentClient;
import vent.servicerequest.client.AssessmentClientException;
import vent.servicerequest.client.AssessmentInput;
import vent.servicerequest.client.AssessmentReply;
import vent.servicerequest.domain.AiAssessment;
import vent.servicerequest.domain.AssessmentValidationError;
import vent.servicerequest.domain.ServiceRequest;
import vent.servicerequest.repository.AiAssessmentRepository;
import vent.servicerequest.repository.ServiceRequestRepository;
import vent.servicerequest.rule.AssessmentValidation;
import vent.servicerequest.rule.AssessmentValidator;
import vent.servicerequest.rule.SituationSummary;

/**
 * AI 소견 만들기 유스케이스 (스펙 8.1·8.5·8.6, #10, ADR-0015).
 *
 * <ol>
 *   <li>신청을 맡는다(한 줄 UPDATE). 접수 직후 처리와 재처리 스케줄러가 같은 건을 동시에 처리하지 않는다</li>
 *   <li>상황 요약을 만들고 AI를 부른다. AI를 부르는 동안에는 DB 트랜잭션을 열어 두지 않는다(느린 외부 호출이
 *       DB 연결을 붙잡지 않게)</li>
 *   <li>응답을 검증하고 시도마다 입력 사본과 결과를 저장한다. 실패하면 한 번 더 시도한다. 시도 수는 신청 하나에
 *       최대 {@code max-attempts}번이다(서버가 도중에 꺼졌다 다시 맡아도 늘지 않는다). 다시 해도 같은 실패(안전 거절,
 *       잘못된 요청, 인증 실패)면 더 시도하지 않는다(#12)</li>
 *   <li>통과하면 소견 완료, 끝까지 실패하거나 AI를 부를 수 없거나 상황 요약을 만들 수 없으면 사람 확인 필요</li>
 * </ol>
 * 저장 자체가 실패하면(DB 장애) 신청은 접수됨으로 남고, 맡은 지 오래되면 재처리 스케줄러가 다시 맡는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);
    static final String UNAVAILABLE_MODEL = "unavailable";

    private final ServiceRequestRepository requestRepository;
    private final AiAssessmentRepository assessmentRepository;
    private final SituationSummaryService summaryService;
    private final AssessmentInputFactory inputFactory;
    private final AssessmentClient client;
    private final AssessmentValidator validator;
    private final AssessmentProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Executor assessmentExecutor;

    public AssessmentService(ServiceRequestRepository requestRepository, AiAssessmentRepository assessmentRepository,
                             SituationSummaryService summaryService, AssessmentInputFactory inputFactory,
                             AssessmentClient client, AssessmentValidator validator, AssessmentProperties properties,
                             ObjectMapper objectMapper, PlatformTransactionManager transactionManager,
                             @Qualifier("assessmentExecutor") Executor assessmentExecutor) {
        this.requestRepository = requestRepository;
        this.assessmentRepository = assessmentRepository;
        this.summaryService = summaryService;
        this.inputFactory = inputFactory;
        this.client = client;
        this.validator = validator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.assessmentExecutor = assessmentExecutor;
    }

    /**
     * 신청 하나의 AI 소견을 만든다. 다른 곳이 이미 맡았거나 접수됨이 아니면 아무것도 하지 않는다.
     *
     * @param serviceRequestId A/S 신청 ID
     * @param now              현재 시각
     */
    public void assess(Long serviceRequestId, OffsetDateTime now) {
        Integer claimed = transactionTemplate.execute(status ->
                requestRepository.claimForAssessment(serviceRequestId, now, now.minus(properties.pendingAfter())));
        if (claimed == null || claimed == 0) {
            return;
        }
        // 서버가 도중에 꺼졌다 다시 맡은 경우: 이미 통과한 시도가 있으면 마무리만, 아니면 남은 시도만 한다
        if (assessmentRepository.existsByServiceRequestIdAndPassedTrue(serviceRequestId)) {
            finish(serviceRequestId, true);
            return;
        }
        ServiceRequest request = requestRepository.findById(serviceRequestId).orElseThrow();
        int previous = (int) assessmentRepository.countByServiceRequestId(serviceRequestId);

        SituationSummary summary;
        try {
            summary = summaryService.summarize(request, now);
        } catch (RuntimeException e) {
            log.error("A/S 신청 {} 상황 요약 실패, 사람 확인 필요로 넘김", serviceRequestId, e);
            save(AiAssessment.attempt(serviceRequestId, previous + 1, objectMapper.createObjectNode(), null, null,
                    List.of(new AssessmentValidationError("SUMMARY_ERROR", "상황 요약을 만들 수 없음: " + e.getMessage())),
                    UNAVAILABLE_MODEL, now));
            finish(serviceRequestId, false);
            return;
        }

        boolean passed = false;
        boolean worthRetrying = true;
        for (int attempt = previous + 1; attempt <= properties.maxAttempts() && !passed && worthRetrying; attempt++) {
            Attempt result = callAndValidate(request, summary, attempt);
            AiAssessment saved = save(result.record());
            if (saved == null) {
                // 같은 시도 번호가 이미 있다: 다른 곳이 처리 중이다. 그쪽이 마무리한다
                return;
            }
            passed = saved.isPassed();
            // 안전 거절, 잘못된 요청, 인증 실패는 다시 해도 같다(#12)
            worthRetrying = result.retryable();
        }
        finish(serviceRequestId, passed);
    }

    /**
     * 접수된 채 오래 남은 신청을 다시 처리한다 (서버 재시작, 작업 대기열이 가득 찬 경우).
     *
     * <p>AI 호출은 느리므로 스케줄러 스레드에서 부르지 않고 AI 소견 작업 스레드에 넘긴다. 스케줄러 스레드는 다른 기능의
     * 검사(게이트웨이 무수신, 후드 연동, 명령 재시도)와 함께 쓴다.
     */
    public void retryPending(OffsetDateTime now) {
        for (Long id : requestRepository.findReceivedIdsCreatedBefore(now.minus(properties.pendingAfter()))) {
            assessmentExecutor.execute(() -> {
                try {
                    assess(id, OffsetDateTime.now(ZoneOffset.UTC));
                } catch (RuntimeException e) {
                    log.error("A/S 신청 {} AI 소견 재처리 실패, 다음 주기에 다시 시도", id, e);
                }
            });
        }
    }

    /** AI를 부르고 검증한다. AI를 부를 수 없으면 그 이유를 검증 오류로 남긴다. 시도 시각은 실제로 부른 시각이다. */
    private Attempt callAndValidate(ServiceRequest request, SituationSummary summary, int attempt) {
        AssessmentInput input = inputFactory.create(summary, request.getCustomerMessage(), attempt);
        JsonNode snapshot = objectMapper.valueToTree(input);
        try {
            AssessmentReply reply = client.assess(input);
            AssessmentValidation validation = validator.validate(reply.rawText(), summary.availableMetricKeys(), properties.parts());
            return new Attempt(AiAssessment.attempt(request.getId(), attempt, snapshot, validation.parsed(), reply.rawText(),
                    validation.errors(), reply.model(), OffsetDateTime.now(ZoneOffset.UTC)), true);
        } catch (AssessmentClientException e) {
            // 시간 초과·연결 실패(CLIENT_ERROR), 안전 거절(REFUSAL), 응답 잘림(TRUNCATED) (#12)
            log.warn("A/S 신청 {} AI 소견을 받지 못함 (시도 {}, {}): {}", request.getId(), attempt, e.code(), e.getMessage());
            String model = e.model() == null ? UNAVAILABLE_MODEL : e.model();
            return new Attempt(failedAttempt(request, attempt, snapshot, e.code(), e.getMessage(), model), e.retryable());
        } catch (RuntimeException e) {
            log.warn("A/S 신청 {} AI 호출 실패 (시도 {})", request.getId(), attempt, e);
            return new Attempt(failedAttempt(request, attempt, snapshot, AssessmentClientException.CLIENT_ERROR,
                    "AI를 부를 수 없음: " + e.getMessage(), UNAVAILABLE_MODEL), true);
        }
    }

    private static AiAssessment failedAttempt(ServiceRequest request, int attempt, JsonNode snapshot, String code, String message,
                                              String model) {
        return AiAssessment.attempt(request.getId(), attempt, snapshot, null, null,
                List.of(new AssessmentValidationError(code, message)), model, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 시도 한 번의 기록과, 실패했다면 다음 시도를 해 볼 만한지.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#12)
     */
    private record Attempt(AiAssessment record, boolean retryable) {
    }

    /** 시도 하나를 짧은 트랜잭션으로 저장한다. 같은 시도 번호가 이미 있으면 null. */
    private AiAssessment save(AiAssessment assessment) {
        try {
            return transactionTemplate.execute(status -> assessmentRepository.save(assessment));
        } catch (DataIntegrityViolationException e) {
            log.warn("A/S 신청 {} 시도 {}는 이미 기록됨", assessment.getServiceRequestId(), assessment.getAttempt());
            return null;
        }
    }

    private void finish(Long serviceRequestId, boolean passed) {
        transactionTemplate.executeWithoutResult(status -> requestRepository.findById(serviceRequestId).ifPresent(r -> {
            if (passed) {
                r.markAssessed();
            } else {
                r.markNeedsHuman();
            }
        }));
    }
}
