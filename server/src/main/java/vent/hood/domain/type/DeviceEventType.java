package vent.hood.domain.type;

/**
 * 장비 이벤트 종류 (스펙 4.2, 5장 device_event).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public enum DeviceEventType {
    /** VEMC가 주방 후드나 배기팬이 켜진 것을 감지했다 */
    HOOD_ON,
    /** 후드나 배기팬이 꺼졌다 */
    HOOD_OFF
}
