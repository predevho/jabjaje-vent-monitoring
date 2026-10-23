package vent.site;

/** 게이트웨이 연결 상태: 연결 전, 연결됨, 일시 끊김, 오프라인. */
public enum ConnectionState {
    /** 등록만 되고 아직 한 번도 연결되지 않음. 이상을 만들지 않는다. */
    PENDING,
    CONNECTED,
    INTERRUPTED,
    OFFLINE
}
