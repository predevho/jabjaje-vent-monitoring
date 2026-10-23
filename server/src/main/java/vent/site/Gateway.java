package vent.site;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

/** 게이트웨이: 현장마다 하나인 연결 통로. 장비가 아니다. */
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
