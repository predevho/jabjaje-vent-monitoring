package vent.servicerequest.domain.type;

/**
 * 판정에서 AI 소견을 고칠 수 있는 항목 (#11). {@code verdict.edited_fields}에 이 이름으로 저장한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public enum VerdictField {
    /** 원인 (가능성 순) */
    CAUSES,
    /** 기사가 할 조치 */
    TECHNICIAN_ACTIONS,
    /** 부품 */
    PARTS,
    /** 방문 필요 여부 */
    NEEDS_VISIT,
    /** 권하는 점검 */
    RECOMMENDED_SERVICES,
    /** 고객 안내문 */
    CUSTOMER_REPLY
}
