package vent.command.service;

/**
 * 잘못된 부스트 요청 (#8). 기록하지 않고 400으로 돌려준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public class BoostRequestException extends RuntimeException {

    public BoostRequestException(String message) {
        super(message);
    }
}
