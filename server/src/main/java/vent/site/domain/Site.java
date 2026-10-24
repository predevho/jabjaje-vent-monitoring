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
import java.math.RoundingMode;
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

    private static final BigDecimal CMH_PER_OCCUPANT = BigDecimal.valueOf(30);
    private static final BigDecimal CEILING_HEIGHT_M = new BigDecimal("2.5");
    private static final BigDecimal AIR_CHANGES_PER_HOUR = new BigDecimal("0.6");

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

    /**
     * 최소 환기량 (CMH, 스펙 5.3, 회사 환기량 산정 기준). 인원 × 30CMH와 바닥면적 × 층고 2.5m × 0.6회/h 중 큰 값을
     * 정수로 올린다(예: 148.5 → 149). 저장하지 않고 인원·면적으로 계산한다.
     *
     * @since 2026-10-24 (#5)
     */
    public int minimumAirflowCmh() {
        return minimumAirflowCmh(occupants, floorAreaM2);
    }

    /** {@link #minimumAirflowCmh()}의 계산식. 엔티티 없이 단위 테스트하려고 따로 둔다. */
    static int minimumAirflowCmh(int occupants, BigDecimal floorAreaM2) {
        BigDecimal byOccupants = CMH_PER_OCCUPANT.multiply(BigDecimal.valueOf(occupants));
        BigDecimal byArea = floorAreaM2.multiply(CEILING_HEIGHT_M).multiply(AIR_CHANGES_PER_HOUR);
        return byOccupants.max(byArea).setScale(0, RoundingMode.CEILING).intValueExact();
    }

    public Gateway getGateway() {
        return gateway;
    }

    public List<Device> getDevices() {
        return devices;
    }
}
