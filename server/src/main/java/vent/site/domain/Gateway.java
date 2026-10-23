package vent.site.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import vent.site.domain.type.ConnectionState;

/**
 * 게이트웨이 엔티티 ({@code gateway} 테이블).
 *
 * <p>현장마다 하나인 연결 통로. 장비가 아니며, 연결 상태(연결 전, 연결됨, 일시 끊김, 오프라인)를 가진다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@Entity
public class Gateway {

    @Id
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Enumerated(EnumType.STRING)
    private ConnectionState connectionState;

    protected Gateway() {
    }

    public String getId() {
        return id;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }
}
