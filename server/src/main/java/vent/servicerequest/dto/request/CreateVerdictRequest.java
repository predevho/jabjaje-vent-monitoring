package vent.servicerequest.dto.request;

import java.util.List;

/**
 * 판정 요청 본문 (#11). "그대로 판정"은 화면이 미리 채운 AI 소견 내용을 그대로 보낸다.
 *
 * @param causes              확정한 원인 (가능성 순, 하나 이상)
 * @param technicianActions   기사가 할 조치
 * @param parts               필요한 부품 (판매 목록에 있는 이름)
 * @param needsVisit          방문이 필요한지
 * @param recommendedServices 권하는 점검
 * @param customerReply       고객에게 보낼 안내
 * @param decidedBy           판정한 직원 이름 (로그인이 없어 직접 적는다)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public record CreateVerdictRequest(List<String> causes, List<String> technicianActions, List<String> parts,
                                   Boolean needsVisit, List<String> recommendedServices, String customerReply,
                                   String decidedBy) {
}
