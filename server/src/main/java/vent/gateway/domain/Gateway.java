package vent.gateway.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vent.gateway.domain.type.ConnectionState;
import vent.site.domain.Site;

/**
 * 게이트웨이 엔티티 ({@code gateway} 테이블).
 *
 * <p>현장마다 하나인 연결 통로. 장비가 아니며, 연결 상태(연결 전, 연결됨, 일시 끊김, 오프라인)를 가진다.
 * 상태가 바뀌는 규칙은 이 엔티티의 메서드에 모아 둔다(스펙 6.3, #6).
 * <ul>
 *   <li>수신하면 어느 상태에서든 연결됨</li>
 *   <li>연결됨에서 offline 보고(Last Will)나 2분 넘는 무수신이면 일시 끊김</li>
 *   <li>일시 끊김이 30분 넘게 이어지면 오프라인</li>
 *   <li>한 번도 수신하지 않은 연결 전은 검사하지 않는다</li>
 * </ul>
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

    private OffsetDateTime lastReceivedAt;

    private OffsetDateTime disconnectedAt;

    private String firmwareVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> capabilities;

    protected Gateway() {
    }

    /** 연결 전 게이트웨이. 단위 테스트에서 쓴다. 실제 게이트웨이는 등록(시드) 때 연결 전으로 만들어진다. */
    static Gateway pending(String id) {
        Gateway gateway = new Gateway();
        gateway.id = id;
        gateway.connectionState = ConnectionState.PENDING;
        return gateway;
    }

    /**
     * 서버가 이 게이트웨이에서 무언가를 받았음을 기록한다. 어느 상태에서든 연결됨이 된다.
     *
     * @param receivedAt 서버가 받은 시각
     * @since 2026-10-24 (#6)
     */
    public void markReceived(OffsetDateTime receivedAt) {
        if (lastReceivedAt == null || receivedAt.isAfter(lastReceivedAt)) {
            lastReceivedAt = receivedAt;
        }
        connectionState = ConnectionState.CONNECTED;
        disconnectedAt = null;
    }

    /**
     * 게이트웨이의 offline 보고(Last Will)를 받았다. 연결됨일 때만 일시 끊김으로 바꾼다.
     * Last Will은 실제로 끊긴 뒤 keepalive의 1.5배가 지나서 오므로, 끊긴 시각은 마지막 수신 시각으로 둔다.
     *
     * @since 2026-10-24 (#6)
     */
    public void markReportedOffline() {
        if (connectionState == ConnectionState.CONNECTED) {
            interrupt();
        }
    }

    /**
     * 무수신 검사. 연결됨인데 마지막 수신이 기준보다 오래됐으면 일시 끊김, 일시 끊김이 기준보다 오래 이어졌으면 오프라인으로 바꾼다.
     * 한 번 검사에서 두 단계를 모두 지날 수 있다(서버가 오래 꺼져 있었던 경우).
     *
     * @param now              검사 시각
     * @param interruptedAfter 이 시간보다 오래 받지 못하면 일시 끊김 (기본 2분)
     * @param offlineAfter     끊긴 지 이 시간보다 오래되면 오프라인 (기본 30분)
     * @since 2026-10-24 (#6)
     */
    public void checkSilence(OffsetDateTime now, Duration interruptedAfter, Duration offlineAfter) {
        if (connectionState == ConnectionState.CONNECTED && lastReceivedAt != null
                && lastReceivedAt.plus(interruptedAfter).isBefore(now)) {
            interrupt();
        }
        if (connectionState == ConnectionState.INTERRUPTED && disconnectedAt.plus(offlineAfter).isBefore(now)) {
            connectionState = ConnectionState.OFFLINE;
        }
    }

    /**
     * online 보고에 담긴 버전과 지원 기능을 기록한다(스펙 4.4). 연결 상태는 바꾸지 않는다.
     *
     * @since 2026-10-24 (#6)
     */
    public void recordReport(String firmwareVersion, List<String> capabilities) {
        this.firmwareVersion = firmwareVersion;
        this.capabilities = capabilities == null ? null : List.copyOf(capabilities);
    }

    /**
     * 이 게이트웨이가 주어진 현장에 속하는지. 상태 토픽의 현장 ID가 맞는지 확인할 때 쓴다.
     *
     * @param siteId 토픽에서 읽은 현장 ID (문자열)
     * @since 2026-10-24 (#6)
     */
    public boolean belongsTo(String siteId) {
        return String.valueOf(site.getId()).equals(siteId);
    }

    private void interrupt() {
        connectionState = ConnectionState.INTERRUPTED;
        disconnectedAt = lastReceivedAt;
    }

    public String getId() {
        return id;
    }

    public Site getSite() {
        return site;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public OffsetDateTime getLastReceivedAt() {
        return lastReceivedAt;
    }

    public OffsetDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }
}
