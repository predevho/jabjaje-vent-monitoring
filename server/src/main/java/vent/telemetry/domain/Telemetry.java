package vent.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import vent.telemetry.domain.type.TelemetryQuality;

/**
 * 측정값 엔티티 ({@code telemetry} 테이블).
 *
 * <p>게이트웨이가 30초마다 보내는 열회수 환기장치의 상태 수치 한 건. 측정 시각(장비)과 수신 시각(서버)을 따로 가진다.
 * 저장은 중복 무시를 위해 직접 SQL로 하고({@code TelemetryInsertRepository}), 이 엔티티는 조회에 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@Entity
public class Telemetry {

    @Id
    private Long id;

    private String deviceId;

    private UUID messageId;

    private OffsetDateTime measuredAt;

    private OffsetDateTime receivedAt;

    // 기본 이름 규칙은 숫자 뒤나 맨 끝의 대문자 한 글자를 단어 경계로 보지 않아(co2ppm, tempc) 직접 지정한다.
    @Column(name = "co2_ppm")
    private int co2Ppm;

    @Column(name = "temp_c")
    private BigDecimal tempC;

    private BigDecimal humidityPct;

    private int supplyCmh;

    private int exhaustCmh;

    private int filterPa;

    private boolean bypass;

    /** 부스트 중인지 (#8) */
    private boolean boost;

    @Enumerated(EnumType.STRING)
    private TelemetryQuality quality;

    private boolean late;

    protected Telemetry() {
    }

    public TelemetryQuality getQuality() {
        return quality;
    }

    public boolean isLate() {
        return late;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public int getCo2Ppm() {
        return co2Ppm;
    }

    public BigDecimal getTempC() {
        return tempC;
    }

    public BigDecimal getHumidityPct() {
        return humidityPct;
    }

    public int getSupplyCmh() {
        return supplyCmh;
    }

    public int getExhaustCmh() {
        return exhaustCmh;
    }

    public int getFilterPa() {
        return filterPa;
    }

    public boolean isBypass() {
        return bypass;
    }

    public boolean isBoost() {
        return boost;
    }
}
