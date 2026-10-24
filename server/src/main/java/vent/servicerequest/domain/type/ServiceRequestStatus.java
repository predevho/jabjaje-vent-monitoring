package vent.servicerequest.domain.type;

/**
 * A/S 신청 상태 (스펙 5장, 8.1).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public enum ServiceRequestStatus {
    /** 접수됨: 저장했고 AI 소견을 기다린다 */
    RECEIVED,
    /** 소견 완료: AI 소견이 검증을 통과했다. 직원 판정을 기다린다 */
    ASSESSED,
    /** 사람 확인 필요: AI 소견이 재시도 뒤에도 검증을 통과하지 못했거나 AI를 부를 수 없었다. 직원이 처음부터 판정한다 */
    NEEDS_HUMAN,
    /** 판정됨: 직원이 판정했다 (#11) */
    DECIDED
}
