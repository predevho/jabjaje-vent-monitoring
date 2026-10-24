package vent.servicerequest.service;

/**
 * 지금은 판정할 수 없는 A/S 신청 (#11). AI 소견을 만드는 중이거나 이미 판정됐다. 메시지는 그대로 화면에 보여 준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public class VerdictConflictException extends RuntimeException {

    public VerdictConflictException(String message) {
        super(message);
    }
}
