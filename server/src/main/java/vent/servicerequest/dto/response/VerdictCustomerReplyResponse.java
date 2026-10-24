package vent.servicerequest.dto.response;

import java.time.OffsetDateTime;
import vent.servicerequest.domain.type.ServiceRequestStatus;

/**
 * 고객에게 보여 줄 안내 응답 (#11). 판정 전에는 안내문이 없다. AI 소견의 안내문 초안은 여기 나오지 않는다(ADR-0005).
 *
 * @param status    A/S 신청 상태
 * @param reply     판정에서 확정한 고객 안내. 판정 전이면 null
 * @param decidedAt 판정 시각. 판정 전이면 null
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public record VerdictCustomerReplyResponse(ServiceRequestStatus status, String reply, OffsetDateTime decidedAt) {
}
