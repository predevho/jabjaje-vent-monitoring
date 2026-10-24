package vent.servicerequest.service;

/**
 * 잘못된 A/S 신청 (#10). 메시지는 그대로 화면에 보여 준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public class ServiceRequestException extends RuntimeException {

    public ServiceRequestException(String message) {
        super(message);
    }
}
