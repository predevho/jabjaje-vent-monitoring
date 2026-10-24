package vent.command.domain.type;

/**
 * 원격 명령 종류 (스펙 5장 command).
 *
 * <p>부스트 취소({@code BOOST_CANCEL})는 아직 없다. 지속시간이 지나면 장비가 스스로 원래대로 돌아간다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public enum RemoteCommandType {
    /** 부스트: 정해진 시간 동안 급기와 배기를 함께 올린다 (용어집) */
    BOOST
}
