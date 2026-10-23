package vent.site.domain;

/**
 * 장비 종류.
 *
 * <p>게이트웨이는 장비가 아니라서 여기에 없다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public enum DeviceType {
    /** 열회수 환기장치 */
    VENTILATOR,
    /** 후드 연동 컨트롤러 */
    VEMC
}
