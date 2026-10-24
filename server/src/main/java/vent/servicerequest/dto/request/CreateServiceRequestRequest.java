package vent.servicerequest.dto.request;

/**
 * A/S 신청 접수 요청 본문 (#10).
 *
 * @param siteId          현장 ID
 * @param deviceId        장비 ID. 고객이 장비를 특정하지 못하면 비운다
 * @param customerMessage 고객 문의
 * @param customerName    고객 이름 (직원만 본다)
 * @param contactPhone    연락처 (직원만 본다)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record CreateServiceRequestRequest(Long siteId, String deviceId, String customerMessage, String customerName,
                                          String contactPhone) {
}
