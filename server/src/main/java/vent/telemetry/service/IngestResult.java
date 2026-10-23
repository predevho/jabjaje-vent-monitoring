package vent.telemetry.service;

/**
 * 측정값 수신 처리 결과.
 *
 * <p>저장되지 않은 경우 그 이유를 나타낸다. 엔티티가 쓰는 값이 아니라 {@link TelemetryIngestService}가 돌려주는 결과라 service 레이어에 둔다.
 * 수신 처리기가 이 값으로 로그를 남긴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public enum IngestResult {
    /** 저장됨 */
    STORED,
    /** 이미 같은 messageId가 저장되어 무시함 (QoS 1 중복 전달) */
    DUPLICATE,
    /** 토픽의 장비 ID와 메시지의 장비 ID가 다름 */
    DEVICE_ID_MISMATCH,
    /** 토픽의 현장 ID가 장비가 속한 현장과 다름 */
    SITE_MISMATCH,
    /** 등록되지 않은 장비 */
    UNKNOWN_DEVICE,
    /** 필수 값이 빠진 메시지 */
    INVALID
}
