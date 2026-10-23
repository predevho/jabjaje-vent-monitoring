package vent.site;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 장비: 열회수 환기장치 또는 VEMC. */
@Entity
public class Device {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    // gateway_id is part of a composite FK with site_id; read-only here so the two never diverge in code.
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
