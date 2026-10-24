package vent.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.gateway.domain.type.ConnectionState;

/**
 * 게이트웨이 연결 상태 전이 단위 테스트.
 *
 * <p>테스트 지점: 게이트웨이 엔티티의 공개 메서드(수신, offline 보고, 무수신 검사). 기준은 스펙 6.3의 2분, 30분이고
 * 시각은 직접 적은 값으로 넣는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
class GatewayTest {

    private static final Duration INTERRUPTED_AFTER = Duration.ofMinutes(2);
    private static final Duration OFFLINE_AFTER = Duration.ofMinutes(30);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("연결됨 → 2분 넘게 무수신이면 일시 끊김 → 끊긴 지 30분 넘으면 오프라인 → 다시 수신하면 연결됨")
    void connectedInterruptedOfflineConnected() {
        Gateway gateway = Gateway.pending("gw-s1");

        gateway.markReceived(T0);
        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.CONNECTED);

        gateway.checkSilence(T0.plusMinutes(2).plusSeconds(1), INTERRUPTED_AFTER, OFFLINE_AFTER);
        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.INTERRUPTED);
        assertThat(gateway.getDisconnectedAt()).isEqualTo(T0);

        gateway.checkSilence(T0.plusMinutes(30).plusSeconds(1), INTERRUPTED_AFTER, OFFLINE_AFTER);
        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.OFFLINE);

        gateway.markReceived(T0.plusMinutes(47));
        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(gateway.getLastReceivedAt()).isEqualTo(T0.plusMinutes(47));
        assertThat(gateway.getDisconnectedAt()).isNull();
    }

    @Test
    @DisplayName("일시 끊김이 30분을 넘기 전에 다시 수신하면 오프라인을 거치지 않고 연결됨으로 돌아온다")
    void interruptedReconnectsBeforeOffline() {
        Gateway gateway = Gateway.pending("gw-s1");
        gateway.markReceived(T0);
        gateway.markReportedOffline();

        gateway.markReceived(T0.plusMinutes(5));
        gateway.checkSilence(T0.plusMinutes(6), INTERRUPTED_AFTER, OFFLINE_AFTER);

        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(gateway.getDisconnectedAt()).isNull();
    }

    @Test
    @DisplayName("무수신 2분 정각은 아직 연결됨이다 (넘어야 끊김)")
    void exactlyTwoMinutesIsStillConnected() {
        Gateway gateway = Gateway.pending("gw-s1");
        gateway.markReceived(T0);

        gateway.checkSilence(T0.plusMinutes(2), INTERRUPTED_AFTER, OFFLINE_AFTER);

        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.CONNECTED);
    }

    @Test
    @DisplayName("offline 보고(Last Will)를 받으면 일시 끊김이 되고, 끊긴 시각은 마지막 수신 시각이다")
    void reportedOfflineInterruptsAtLastReceivedTime() {
        Gateway gateway = Gateway.pending("gw-s1");
        gateway.markReceived(T0);

        gateway.markReportedOffline();

        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.INTERRUPTED);
        assertThat(gateway.getDisconnectedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("한 번도 수신하지 않은 연결 전 게이트웨이는 무수신 검사와 offline 보고로 바뀌지 않는다")
    void pendingGatewayIsNotChecked() {
        Gateway gateway = Gateway.pending("gw-s1");

        gateway.checkSilence(T0.plusDays(1), INTERRUPTED_AFTER, OFFLINE_AFTER);
        gateway.markReportedOffline();

        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.PENDING);
    }

    @Test
    @DisplayName("31분 무수신을 한 번에 검사하면 일시 끊김을 거쳐 바로 오프라인이 된다")
    void longSilenceGoesOfflineInOneCheck() {
        Gateway gateway = Gateway.pending("gw-s1");
        gateway.markReceived(T0);

        gateway.checkSilence(T0.plusMinutes(31), INTERRUPTED_AFTER, OFFLINE_AFTER);

        assertThat(gateway.getConnectionState()).isEqualTo(ConnectionState.OFFLINE);
        assertThat(gateway.getDisconnectedAt()).isEqualTo(T0);
    }
}
