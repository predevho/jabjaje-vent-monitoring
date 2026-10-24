package vent.site.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import vent.gateway.domain.Gateway;
import vent.site.domain.type.TargetAirflowSource;

/**
 * 현장 엔티티 ({@code site} 테이블).
 *
 * <p>장비가 설치된 건물 하나. 바닥면적, 거주 인원, 목표 풍량을 가지며 게이트웨이 하나와 장비 여러 대가 속한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@Entity
public class Site {

    @Id
    private Long id;

    private String name;

    private String region;

    @Column(name = "floor_area_m2")
    private BigDecimal floorAreaM2;

    private int occupants;

    private int targetAirflowCmh;

    @Enumerated(EnumType.STRING)
    private TargetAirflowSource targetAirflowSource;

    @OneToOne(mappedBy = "site")
    private Gateway gateway;

    @OneToMany(mappedBy = "site")
    @OrderBy("id")
    private List<Device> devices = new ArrayList<>();

    protected Site() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public BigDecimal getFloorAreaM2() {
        return floorAreaM2;
    }

    public int getOccupants() {
        return occupants;
    }

    public int getTargetAirflowCmh() {
        return targetAirflowCmh;
    }

    public TargetAirflowSource getTargetAirflowSource() {
        return targetAirflowSource;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public List<Device> getDevices() {
        return devices;
    }
}
