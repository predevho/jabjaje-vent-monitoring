package vent.servicerequest.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vent.servicerequest.dto.request.CreateServiceRequestRequest;
import vent.servicerequest.dto.request.CreateVerdictRequest;
import vent.servicerequest.dto.response.VerdictCustomerReplyResponse;
import vent.servicerequest.dto.response.ServiceRequestResponse;
import vent.servicerequest.dto.response.VerdictResponse;
import vent.servicerequest.service.ServiceRequestException;
import vent.servicerequest.service.ServiceRequestService;
import vent.servicerequest.service.VerdictConflictException;
import vent.servicerequest.service.VerdictService;

/**
 * A/S 신청 API (#10).
 *
 * <pre>
 * POST /api/service-requests        접수 → 201, 상태는 접수됨. AI 소견은 백그라운드에서 만든다
 * GET  /api/service-requests        최근 신청 20개
 * GET  /api/service-requests/{id}   신청 하나와 AI 소견 시도들, 판정
 * POST /api/service-requests/{id}/verdict          판정 → 201, 신청은 판정됨 (#11)
 * GET  /api/service-requests/{id}/customer-reply   고객에게 보여 줄 안내. 판정 전이면 안내문 없음 (#11)
 * GET  /api/service-requests/parts                 판정에 고를 수 있는 판매 부품 목록 (#11)
 * </pre>
 * 잘못된 요청은 400과 안내 문구, 없는 현장·신청은 404, 지금 판정할 수 없는 신청(AI 소견을 만드는 중, 이미 판정됨)은 409다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@RestController
@RequestMapping("/api/service-requests")
public class ServiceRequestController {

    private final ServiceRequestService requestService;
    private final VerdictService verdictService;

    public ServiceRequestController(ServiceRequestService requestService, VerdictService verdictService) {
        this.requestService = requestService;
        this.verdictService = verdictService;
    }

    @PostMapping
    public ResponseEntity<ServiceRequestResponse> create(@RequestBody CreateServiceRequestRequest body) {
        ServiceRequestResponse created = requestService.create(body, OffsetDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<ServiceRequestResponse> recent() {
        return requestService.findRecent();
    }

    @GetMapping("/{id}")
    public ServiceRequestResponse detail(@PathVariable Long id) {
        return requestService.findDetail(id);
    }

    @PostMapping("/{id}/verdict")
    public ResponseEntity<VerdictResponse> decide(@PathVariable Long id, @RequestBody CreateVerdictRequest body) {
        VerdictResponse verdict = verdictService.decide(id, body, OffsetDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(verdict);
    }

    @GetMapping("/{id}/customer-reply")
    public VerdictCustomerReplyResponse customerReply(@PathVariable Long id) {
        return verdictService.customerReply(id);
    }

    @GetMapping("/parts")
    public List<String> parts() {
        return verdictService.partCatalog();
    }

    @ExceptionHandler(VerdictConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(VerdictConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ServiceRequestException.class)
    public ResponseEntity<Map<String, String>> invalid(ServiceRequestException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }
}
