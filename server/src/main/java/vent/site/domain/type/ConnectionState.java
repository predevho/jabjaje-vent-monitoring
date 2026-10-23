package vent.site.domain.type;

/**
 * 게이트웨이 연결 상태.
 *
 * <p>연결 전 → 연결됨 → 일시 끊김 → 오프라인 순으로 바뀌며, 다시 수신하면 연결됨으로 돌아온다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public enum ConnectionState {
    /** 연결 전: 등록만 되고 아직 한 번도 연결되지 않음. 이상을 만들지 않는다. */
    PENDING,
    /** 연결됨: 최근에 측정값이나 상태를 보내 왔다. */
    CONNECTED,
    /** 일시 끊김: 연결이 짧게 끊겼다. 화면에만 표시하고 이상을 만들지 않는다. */
    INTERRUPTED,
    /** 오프라인: 끊긴 상태가 오래 이어져 이상으로 다룬다. */
    OFFLINE
}
