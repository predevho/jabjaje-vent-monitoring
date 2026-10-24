package vent.servicerequest.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import vent.servicerequest.domain.ServiceRequest;
import vent.servicerequest.domain.type.ServiceRequestStatus;

/**
 * A/S 신청 응답 (#10). 직원 화면용이라 고객 이름·연락처를 포함한다.
 *
 * @param assessments AI 소견 시도들 (시도 순서). 목록 조회에서는 비운다
 * @param verdict     판정 (#11). 판정 전이거나 목록 조회면 null
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record ServiceRequestResponse(Long id, Long siteId, String siteName, String deviceId, String customerMessage,
                                     String customerName, String contactPhone, ServiceRequestStatus status,
                                     OffsetDateTime createdAt, List<AiAssessmentResponse> assessments,
                                     VerdictResponse verdict) {

    public static ServiceRequestResponse from(ServiceRequest r, String siteName, List<AiAssessmentResponse> assessments,
                                              VerdictResponse verdict) {
        return new ServiceRequestResponse(r.getId(), r.getSiteId(), siteName, r.getDeviceId(), r.getCustomerMessage(),
                r.getCustomerName(), r.getContactPhone(), r.getStatus(), r.getCreatedAt(), assessments, verdict);
    }
}
