package vent.gateway.service;

/**
 * 게이트웨이 상태 메시지 처리 결과.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
public enum GatewayStatusResult {
    /** 반영했다 */
    APPLIED,
    /** online도 offline도 아닌 메시지 */
    INVALID,
    /** 등록되지 않은 게이트웨이 */
    UNKNOWN_GATEWAY,
    /** 토픽의 현장 ID가 게이트웨이의 현장과 다르다 (다른 현장 행세, 스펙 9.2 S3) */
    SITE_MISMATCH
}
