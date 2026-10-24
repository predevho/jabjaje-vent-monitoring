package vent.command.rule;

import vent.command.domain.BoostParams;

/**
 * 부스트 보내기 전 검사 결과.
 *
 * @param kind    보냄, 게이트웨이가 연결되지 않아 보내지 않음, 잘못된 요청
 * @param params  보낼 때의 파라미터 (보낼 때와 보내지 않을 때. 잘못된 요청이면 null)
 * @param message 잘못된 요청일 때 화면에 보일 안내 (그 밖에는 null. 보내지 않은 명령의 안내는 응답 DTO가 상태로 만든다)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record BoostDecision(Kind kind, BoostParams params, String message) {

    /**
     * 검사 결과 종류.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#8)
     */
    public enum Kind {
        /** 보낸다 */
        SEND,
        /** 요청은 맞지만 게이트웨이가 연결됨이 아니라 보내지 않는다. REJECTED_OFFLINE으로 기록한다 */
        REJECT_OFFLINE,
        /** 요청이 잘못됐다. 기록하지 않고 오류로 돌려준다 */
        INVALID
    }

    static BoostDecision send(BoostParams params) {
        return new BoostDecision(Kind.SEND, params, null);
    }

    static BoostDecision rejectOffline(BoostParams params) {
        return new BoostDecision(Kind.REJECT_OFFLINE, params, null);
    }

    static BoostDecision invalid(String message) {
        return new BoostDecision(Kind.INVALID, null, message);
    }
}
