package vent.hood.service;

/**
 * 장비 이벤트 처리 결과.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public enum DeviceEventIngestResult {
    /** 새로 저장했다 */
    STORED,
    /** 같은 messageId가 이미 있어 무시했다 */
    DUPLICATE,
    /** 필수 값이 없거나 모르는 종류다 */
    INVALID,
    /** 토픽의 장비 ID와 메시지의 장비 ID가 다르다 */
    DEVICE_ID_MISMATCH,
    /** 등록되지 않은 장비다 */
    UNKNOWN_DEVICE,
    /** 토픽의 현장 ID가 장비의 현장과 다르다 (스펙 9.2 S3) */
    SITE_MISMATCH,
    /** VEMC가 아닌 장비가 보냈다. 후드 이벤트는 VEMC만 보낸다 */
    NOT_VEMC
}
