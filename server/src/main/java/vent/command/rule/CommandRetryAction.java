package vent.command.rule;

/**
 * 원격 명령 재시도·시간 초과 판단 결과 (#9).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
public enum CommandRetryAction {
    /** 아직 기다린다 */
    WAIT,
    /** 같은 commandId로 다시 보낸다 */
    RETRY,
    /** 시간 초과로 바꾼다 */
    TIME_OUT
}
