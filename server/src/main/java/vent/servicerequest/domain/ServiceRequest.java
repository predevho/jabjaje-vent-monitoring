package vent.servicerequest.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import vent.servicerequest.domain.type.ServiceRequestStatus;

/**
 * A/S 신청 엔티티 ({@code service_request} 테이블).
 *
 * <p>고객이 불편이나 고장을 알려 와 접수된 한 건(용어집). 고객 이름·연락처는 직원만 보고 AI에는 보내지 않는다(#10).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Entity
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long siteId;

    private String deviceId;

    private String customerMessage;

    private String customerName;

    private String contactPhone;

    @Enumerated(EnumType.STRING)
    private ServiceRequestStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime assessmentStartedAt;

    protected ServiceRequest() {
    }

    /** 접수된 A/S 신청. */
    public static ServiceRequest received(Long siteId, String deviceId, String customerMessage, String customerName,
                                          String contactPhone, OffsetDateTime now) {
        ServiceRequest request = new ServiceRequest();
        request.siteId = siteId;
        request.deviceId = deviceId;
        request.customerMessage = customerMessage;
        request.customerName = customerName;
        request.contactPhone = contactPhone;
        request.status = ServiceRequestStatus.RECEIVED;
        request.createdAt = now;
        return request;
    }

    /** AI 소견이 검증을 통과했다. 접수됨일 때만 바꾼다. */
    public void markAssessed() {
        if (status == ServiceRequestStatus.RECEIVED) {
            status = ServiceRequestStatus.ASSESSED;
        }
    }

    /** 사람이 처음부터 판정해야 한다. 접수됨일 때만 바꾼다. */
    public void markNeedsHuman() {
        if (status == ServiceRequestStatus.RECEIVED) {
            status = ServiceRequestStatus.NEEDS_HUMAN;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getCustomerMessage() {
        return customerMessage;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public ServiceRequestStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getAssessmentStartedAt() {
        return assessmentStartedAt;
    }
}
