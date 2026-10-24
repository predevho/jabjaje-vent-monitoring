package vent.anomaly.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;
import vent.anomaly.domain.type.AnomalyStatus;

/**
 * 이상 엔티티 ({@code anomaly} 테이블).
 *
 * <p>규칙이 측정값이나 연결 상태에서 감지한 문제 하나. 대상은 장비 또는 게이트웨이 중 하나이고,
 * 열림(OPEN)으로 생겨 해제 조건이 맞으면 해결(RESOLVED)된다. 시각은 판단 근거가 된 측정값의 측정 시각이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@Entity
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;

    private String gatewayId;

    @Enumerated(EnumType.STRING)
    private AnomalyRuleName rule;

    @Enumerated(EnumType.STRING)
    private AnomalySeverity severity;

    @Enumerated(EnumType.STRING)
    private AnomalyStatus status;

    private String message;

    private OffsetDateTime openedAt;

    private OffsetDateTime resolvedAt;

    protected Anomaly() {
    }

    /** 장비를 대상으로 열린 이상을 만든다. */
    public static Anomaly openForDevice(String deviceId, AnomalyRuleName rule, AnomalySeverity severity,
                                        String message, OffsetDateTime openedAt) {
        Anomaly anomaly = open(rule, severity, message, openedAt);
        anomaly.deviceId = deviceId;
        return anomaly;
    }

    /**
     * 게이트웨이를 대상으로 열린 이상을 만든다.
     *
     * @since 2026-10-24 (#6)
     */
    public static Anomaly openForGateway(String gatewayId, AnomalyRuleName rule, AnomalySeverity severity,
                                         String message, OffsetDateTime openedAt) {
        Anomaly anomaly = open(rule, severity, message, openedAt);
        anomaly.gatewayId = gatewayId;
        return anomaly;
    }

    /** 대상만 비워 둔 열린 이상. 대상(장비 또는 게이트웨이)은 부르는 쪽이 채운다. */
    private static Anomaly open(AnomalyRuleName rule, AnomalySeverity severity, String message, OffsetDateTime openedAt) {
        Anomaly anomaly = new Anomaly();
        anomaly.rule = rule;
        anomaly.severity = severity;
        anomaly.status = AnomalyStatus.OPEN;
        anomaly.message = message;
        anomaly.openedAt = openedAt;
        return anomaly;
    }

    /**
     * 열린 이상의 심각도를 올리고 설명을 바꾼다. 더 무거운 심각도일 때만 바꾸고, 해결될 때까지 내리지 않는다 (#5).
     *
     * @return 바꿨으면 true
     * @since 2026-10-24 (#5)
     */
    public boolean escalate(AnomalySeverity newSeverity, String newMessage) {
        if (status != AnomalyStatus.OPEN || !newSeverity.isMoreSevereThan(severity)) {
            return false;
        }
        severity = newSeverity;
        message = newMessage;
        return true;
    }

    /** 해결로 닫는다. 이미 해결된 이상이면 아무것도 하지 않는다. */
    public void resolve(OffsetDateTime resolvedAt) {
        if (status == AnomalyStatus.OPEN) {
            status = AnomalyStatus.RESOLVED;
            this.resolvedAt = resolvedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public AnomalyRuleName getRule() {
        return rule;
    }

    public AnomalySeverity getSeverity() {
        return severity;
    }

    public AnomalyStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
