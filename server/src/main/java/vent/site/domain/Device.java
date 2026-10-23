package vent.site.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 장비 엔티티 ({@code device} 테이블).
 *
 * <p>현장에 설치되어 게이트웨이를 통해 상태를 보고하는 열회수 환기장치 또는 VEMC.
 * 장비의 현장과 그 게이트웨이의 현장은 DB 복합 외래키로 항상 같다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@Entity
public class Device {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    // gateway_id는 site_id와 함께 복합 외래키를 이룬다. 코드에서 둘이 어긋나지 않도록 읽기 전용으로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gateway_id", insertable = false, updatable = false)
    private Gateway gateway;

    @Enumerated(EnumType.STRING)
    private DeviceType type;

    private String model;

    protected Device() {
    }

    public String getId() {
        return id;
    }

    public DeviceType getType() {
        return type;
    }

    public String getModel() {
        return model;
    }
}
