package vent.servicerequest.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.servicerequest.domain.ServiceRequest;
import vent.servicerequest.dto.request.CreateServiceRequestRequest;
import vent.servicerequest.dto.response.AiAssessmentResponse;
import vent.servicerequest.dto.response.ServiceRequestResponse;
import vent.servicerequest.repository.AiAssessmentRepository;
import vent.servicerequest.repository.ServiceRequestRepository;
import vent.site.domain.Site;
import vent.site.repository.DeviceRepository;
import vent.site.repository.SiteRepository;

/**
 * A/S 신청 유스케이스 (스펙 8.1, #10).
 *
 * <p>접수는 저장만 하고 바로 응답한다. AI 소견은 커밋 뒤 백그라운드에서 만든다({@link ServiceRequestReceivedEvent}).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Service
public class ServiceRequestService {

    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int RECENT_LIMIT = 20;

    private final ServiceRequestRepository requestRepository;
    private final AiAssessmentRepository assessmentRepository;
    private final SiteRepository siteRepository;
    private final DeviceRepository deviceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ServiceRequestService(ServiceRequestRepository requestRepository, AiAssessmentRepository assessmentRepository,
                                 SiteRepository siteRepository, DeviceRepository deviceRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.requestRepository = requestRepository;
        this.assessmentRepository = assessmentRepository;
        this.siteRepository = siteRepository;
        this.deviceRepository = deviceRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * A/S 신청을 접수한다.
     *
     * @throws NoSuchElementException  현장이 없을 때
     * @throws ServiceRequestException 문의가 비었거나 너무 길 때, 장비가 그 현장 것이 아닐 때
     */
    @Transactional
    public ServiceRequestResponse create(CreateServiceRequestRequest body, OffsetDateTime now) {
        if (body.siteId() == null) {
            throw new ServiceRequestException("현장을 골라 주세요");
        }
        Site site = siteRepository.findById(body.siteId())
                .orElseThrow(() -> new NoSuchElementException("현장 없음: " + body.siteId()));
        String message = body.customerMessage() == null ? "" : body.customerMessage().strip();
        if (message.isEmpty()) {
            throw new ServiceRequestException("문의 내용을 적어 주세요");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ServiceRequestException("문의 내용은 " + MAX_MESSAGE_LENGTH + "자까지 적을 수 있어요");
        }
        String deviceId = blankToNull(body.deviceId());
        if (deviceId != null && deviceRepository.findById(deviceId).filter(d -> d.getSite().getId().equals(site.getId())).isEmpty()) {
            throw new ServiceRequestException("그 현장의 장비가 아니에요: " + deviceId);
        }
        String name = limit(blankToNull(body.customerName()), 50, "고객 이름은");
        String phone = limit(blankToNull(body.contactPhone()), 30, "연락처는");

        ServiceRequest saved = requestRepository.save(ServiceRequest.received(site.getId(), deviceId, message, name, phone, now));
        eventPublisher.publishEvent(new ServiceRequestReceivedEvent(saved.getId()));
        return ServiceRequestResponse.from(saved, site.getName(), List.of());
    }

    /** 최근 신청 20개. 소견 시도는 상세 조회에서 본다. */
    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> findRecent() {
        List<ServiceRequest> requests = requestRepository.findAllByOrderByCreatedAtDesc(Limit.of(RECENT_LIMIT));
        Map<Long, String> siteNames = siteRepository.findAllById(requests.stream().map(ServiceRequest::getSiteId).distinct().toList())
                .stream().collect(Collectors.toMap(Site::getId, Site::getName, (a, b) -> a));
        return requests.stream()
                .map(r -> ServiceRequestResponse.from(r, siteNames.get(r.getSiteId()), List.of()))
                .toList();
    }

    /** 신청 하나와 AI 소견 시도 전체. */
    @Transactional(readOnly = true)
    public ServiceRequestResponse findDetail(Long id) {
        ServiceRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("A/S 신청 없음: " + id));
        String siteName = siteRepository.findById(request.getSiteId()).map(Site::getName).orElse(null);
        List<AiAssessmentResponse> assessments = assessmentRepository.findByServiceRequestIdOrderByAttemptAsc(id).stream()
                .map(AiAssessmentResponse::from)
                .toList();
        return ServiceRequestResponse.from(request, siteName, assessments);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String limit(String value, int max, String labelWithParticle) {
        if (value != null && value.length() > max) {
            throw new ServiceRequestException(labelWithParticle + " " + max + "자까지 적을 수 있어요");
        }
        return value;
    }
}
