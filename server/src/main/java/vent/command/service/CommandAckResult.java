package vent.command.service;

/**
 * 원격 명령 응답 처리 결과.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public enum CommandAckResult {
    /** 명령 상태에 반영했다 (적용됨이든 거절됨이든) */
    RECORDED,
    /** 이미 끝난 명령의 응답이라 무시했다 (QoS 1 중복, 새 명령에 밀린 명령) */
    ALREADY_FINAL,
    /** 모르는 commandId */
    UNKNOWN_COMMAND,
    /** 토픽의 장비·현장이 명령의 장비와 다르다 (다른 장비 행세, 스펙 9.2 S3) */
    DEVICE_MISMATCH,
    /** 결과가 APPLIED도 REJECTED도 아니다 */
    INVALID
}
