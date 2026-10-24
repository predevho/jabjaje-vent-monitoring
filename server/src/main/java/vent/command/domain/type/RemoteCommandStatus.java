package vent.command.domain.type;

/**
 * 원격 명령 상태 (스펙 5장 command, 7.1).
 *
 * <p>시간 초과({@code TIMEOUT})와 밀려남({@code SUPERSEDED})은 #9에서 추가한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public enum RemoteCommandStatus {
    /** 보냄: 게이트웨이로 보냈고 장비 응답을 기다린다 */
    SENT,
    /** 적용됨: 장비가 적용했다 */
    APPLIED,
    /** 거절됨: 장비가 자기 보호나 후드 연동 때문에 거절했다. 사유가 함께 남는다 */
    REJECTED,
    /** 게이트웨이가 연결되지 않아 보내지 않음: 연결 전·일시 끊김·오프라인이라 서버가 보내지 않았다 */
    REJECTED_OFFLINE;

    /** 장비 응답을 더 기다리지 않는 상태인지. */
    public boolean isFinal() {
        return this != SENT;
    }
}
