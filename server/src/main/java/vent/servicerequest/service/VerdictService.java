package vent.servicerequest.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.servicerequest.domain.AiAssessment;
import vent.servicerequest.domain.ServiceRequest;
import vent.servicerequest.domain.Verdict;
import vent.servicerequest.domain.VerdictContent;
import vent.servicerequest.domain.type.ServiceRequestStatus;
import vent.servicerequest.domain.type.VerdictField;
import vent.servicerequest.dto.request.CreateVerdictRequest;
import vent.servicerequest.dto.response.VerdictCustomerReplyResponse;
import vent.servicerequest.dto.response.VerdictResponse;
import vent.servicerequest.repository.AiAssessmentRepository;
import vent.servicerequest.repository.ServiceRequestRepository;
import vent.servicerequest.repository.VerdictRepository;
import vent.servicerequest.rule.VerdictEditDetector;

/**
 * 판정 유스케이스 (스펙 8.1, ADR-0005, #11).
 *
 * <p>직원이 AI 소견을 그대로 쓰거나 고쳐서, 또는 사람 확인 필요 건을 처음부터 판정한다. 판정 저장과 신청의 판정됨 변경은
 * 한 트랜잭션이다. 두 직원이 같은 신청을 동시에 판정하지 못하게 신청 행을 잠그고, 신청당 판정 하나는 유니크 제약으로 한 번
 * 더 막는다. 고쳤는지는 화면이 보낸 표시가 아니라 서버가 AI 소견과 비교해 정한다.
 *
 * <p>AI 소견과 달리 고객 안내문의 금액·무상 표현을 막지 않는다. 직원이 마지막 안전장치이고, 보증 수리처럼 실제로 약속해야
 * 하는 경우가 있다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
@Service
public class VerdictService {

    private static final Logger log = LoggerFactory.getLogger(VerdictService.class);
    static final int MAX_REPLY_LENGTH = 2000;
    static final int MAX_NAME_LENGTH = 50;

    private final ServiceRequestRepository requestRepository;
    private final AiAssessmentRepository assessmentRepository;
    private final VerdictRepository verdictRepository;
    private final VerdictEditDetector editDetector;
    private final AssessmentProperties properties;

    public VerdictService(ServiceRequestRepository requestRepository, AiAssessmentRepository assessmentRepository,
                          VerdictRepository verdictRepository, VerdictEditDetector editDetector,
                          AssessmentProperties properties) {
        this.requestRepository = requestRepository;
        this.assessmentRepository = assessmentRepository;
        this.verdictRepository = verdictRepository;
        this.editDetector = editDetector;
        this.properties = properties;
    }

    /**
     * 판정한다.
     *
     * @throws NoSuchElementException    신청이 없을 때
     * @throws VerdictConflictException  AI 소견을 만드는 중이거나 이미 판정됐을 때
     * @throws ServiceRequestException   필수 항목이 비었거나 판매 목록에 없는 부품일 때
     */
    @Transactional
    public VerdictResponse decide(Long serviceRequestId, CreateVerdictRequest body, OffsetDateTime now) {
        ServiceRequest request = requestRepository.findByIdForUpdate(serviceRequestId)
                .orElseThrow(() -> new NoSuchElementException("A/S 신청 없음: " + serviceRequestId));
        // 판정할 수 있는 상태는 엔티티가 한 번 더 막는다(markDecided). 여기서는 화면에 보여 줄 이유를 고른다
        switch (request.getStatus()) {
            case RECEIVED -> throw new VerdictConflictException("AI 소견을 만드는 중이에요. 잠시 뒤에 판정해 주세요");
            case DECIDED -> throw new VerdictConflictException("이미 판정된 A/S 신청이에요");
            case ASSESSED, NEEDS_HUMAN -> {
                // 판정할 수 있다
            }
        }
        VerdictContent content = validated(body);
        String decidedBy = required(body.decidedBy(), "담당자 이름을 적어 주세요");
        if (decidedBy.length() > MAX_NAME_LENGTH) {
            throw new ServiceRequestException("담당자 이름은 " + MAX_NAME_LENGTH + "자까지 적을 수 있어요");
        }

        // 소견 완료면 통과한 AI 소견을 참고한 것으로 본다. 사람 확인 필요면 처음부터 판정한 것이다
        AiAssessment reference = request.getStatus() == ServiceRequestStatus.ASSESSED
                ? assessmentRepository.findFirstByServiceRequestIdAndPassedTrueOrderByAttemptDesc(serviceRequestId).orElse(null)
                : null;
        if (request.getStatus() == ServiceRequestStatus.ASSESSED && reference == null) {
            // 소견 완료인데 통과한 AI 소견이 없다: 데이터가 어긋났다. 판정은 막지 않고 처음부터 판정한 것으로 남긴다
            log.warn("A/S 신청 {}는 소견 완료인데 통과한 AI 소견이 없음, 처음부터 판정으로 기록", serviceRequestId);
        }
        List<VerdictField> editedFields = reference == null ? List.of()
                : editDetector.editedFields(content, VerdictContent.fromAssessment(reference.getResult()));

        Verdict verdict = verdictRepository.save(Verdict.decided(serviceRequestId, reference == null ? null : reference.getId(),
                content, editedFields, decidedBy, now));
        request.markDecided();
        return VerdictResponse.from(verdict);
    }

    /** 고객에게 보여 줄 안내. 판정 전이면 안내문이 없다(ADR-0005). */
    @Transactional(readOnly = true)
    public VerdictCustomerReplyResponse customerReply(Long serviceRequestId) {
        ServiceRequest request = requestRepository.findById(serviceRequestId)
                .orElseThrow(() -> new NoSuchElementException("A/S 신청 없음: " + serviceRequestId));
        return verdictRepository.findByServiceRequestId(serviceRequestId)
                .map(v -> new VerdictCustomerReplyResponse(request.getStatus(), v.getCustomerReply(), v.getDecidedAt()))
                .orElse(new VerdictCustomerReplyResponse(request.getStatus(), null, null));
    }

    /** 판정에 고를 수 있는 판매 부품 목록. */
    public List<String> partCatalog() {
        return properties.parts();
    }

    private VerdictContent validated(CreateVerdictRequest body) {
        List<String> causes = nonBlank(body.causes());
        if (causes.isEmpty()) {
            throw new ServiceRequestException("원인을 하나는 적어 주세요");
        }
        if (body.needsVisit() == null) {
            throw new ServiceRequestException("방문이 필요한지 골라 주세요");
        }
        String reply = required(body.customerReply(), "고객 안내문을 적어 주세요");
        if (reply.length() > MAX_REPLY_LENGTH) {
            throw new ServiceRequestException("고객 안내문은 " + MAX_REPLY_LENGTH + "자까지 적을 수 있어요");
        }
        List<String> parts = nonBlank(body.parts());
        for (String part : parts) {
            if (!properties.parts().contains(part)) {
                throw new ServiceRequestException("판매 목록에 없는 부품이에요: " + part);
            }
        }
        return new VerdictContent(causes, nonBlank(body.technicianActions()), parts, body.needsVisit(),
                nonBlank(body.recommendedServices()), reply);
    }

    private static List<String> nonBlank(List<String> values) {
        return values == null ? List.of()
                : values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ServiceRequestException(message);
        }
        return value.strip();
    }
}
